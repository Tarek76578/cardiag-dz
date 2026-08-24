import argparse
import csv
import json
import os
import urllib.request

SOURCE_URL = "https://github.com/gor3a/vehicle-makes-models"
BATCH_SIZE = 400


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scope", default="all")
    args = ap.parse_args()
    path = os.environ["VEHICLE_ENGINES_CSV"]
    base = os.environ["SUPABASE_URL"].rstrip("/")
    key = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
    with open(path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        raise SystemExit("engines.csv is empty")
    required = {"make", "model", "generation", "gen_year_start", "gen_year_end", "engine_label"}
    missing = required - set(rows[0])
    if missing:
        raise SystemExit("Unexpected engines.csv schema: " + ",".join(sorted(missing)))
    endpoint = f"{base}/functions/v1/import-vehicle-catalog-v2"
    totals = {"rows": len(rows), "models": 0, "generations": 0, "years": 0, "engines": 0, "links": 0}
    for start in range(0, len(rows), BATCH_SIZE):
        batch = rows[start:start+BATCH_SIZE]
        payload = json.dumps({"scope": args.scope, "records": batch}).encode()
        req = urllib.request.Request(endpoint, data=payload, method="POST", headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=180) as response:
                result = json.loads(response.read().decode())
        except Exception as exc:
            raise SystemExit(f"Supabase importer failed at batch {start}: {exc}")
        if not result.get("ok"):
            raise SystemExit(f"Supabase importer failed at batch {start}: {result}")
        for key_name, result_name in (("models", "modelsCreated"), ("generations", "generationsCreated"), ("years", "yearsCreated"), ("engines", "enginesCreated"), ("links", "yearsLinked")):
            totals[key_name] += int(result.get(result_name, 0))
        print(f"batch={start//BATCH_SIZE+1} rows={len(batch)} models={result.get('modelsCreated',0)} generations={result.get('generationsCreated',0)} years={result.get('yearsCreated',0)} engines={result.get('enginesCreated',0)} links={result.get('yearsLinked',0)}")
    if totals["models"] == totals["generations"] == totals["years"] == totals["engines"] == totals["links"] == 0:
        raise SystemExit("Importer completed without creating or linking any catalog records")
    print(json.dumps({"source": SOURCE_URL, "scope": args.scope, **totals}, ensure_ascii=False))


if __name__ == "__main__":
    main()
