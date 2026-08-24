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
    endpoint = f"{base}/functions/v1/import-vehicle-catalog-v4"
    totals = {"rows": len(rows), "canonical": 0}
    for start in range(0, len(rows), BATCH_SIZE):
        batch = rows[start:start+BATCH_SIZE]
        expanded = []
        for r in batch:
            try:
                ys = int(r.get("gen_year_start") or 0)
                ye = int(r.get("gen_year_end") or ys)
            except ValueError:
                continue
            if ys < 1886 or ys > 2100:
                continue
            if ye < ys or ye > 2100:
                ye = ys
            # One canonical record per model year + engine year.
            for year in range(ys, ye + 1):
                eys = r.get("engine_year_start") or year
                eye = r.get("engine_year_end") or year
                try:
                    eys, eye = int(eys), int(eye)
                except ValueError:
                    eys = eye = year
                eys = max(1886, min(2100, eys))
                eye = max(eys, min(2100, eye))
                engine_year = year if eys <= year <= eye else eys
                expanded.append({
                    "source_id": f"{r.get('make','').strip()}|{r.get('model','').strip()}|{r.get('generation','').strip()}|{year}|{r.get('engine_label','').strip()}|{engine_year}",
                    "make_name": r.get("make", "").strip(),
                    "model_name": r.get("model", "").strip(),
                    "generation_name": r.get("generation", "").strip() or None,
                    "model_year": year,
                    "engine_name": r.get("engine_label", "").strip() or None,
                    "engine_year": engine_year,
                    "engine_displacement": r.get("engine_displacement") or None,
                    "engine_cylinders": r.get("engine_cylinders") or None,
                    "engine_power_hp": r.get("engine_power_hp") or None,
                    "transmission": r.get("transmission") or None,
                    "drivetrain": r.get("drivetrain") or None,
                    "fuel_type": r.get("fuel_type") or None,
                    "source_url": SOURCE_URL,
                })
        payload = json.dumps({"scope": args.scope, "records": expanded}).encode()
        req = urllib.request.Request(endpoint, data=payload, method="POST", headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=180) as response:
                result = json.loads(response.read().decode())
        except Exception as exc:
            raise SystemExit(f"Supabase canonical importer failed at batch {start}: {exc}")
        if not result.get("ok"):
            raise SystemExit(f"Supabase canonical importer failed at batch {start}: {result}")
        totals["canonical"] += int(result.get("canonicalCreated", 0))
        print(f"batch={start//BATCH_SIZE+1} source_rows={len(batch)} canonical_candidates={len(expanded)} created={result.get('canonicalCreated',0)}")
    if totals["canonical"] == 0:
        raise SystemExit("Canonical importer completed without writing records")
    print(json.dumps({"source": SOURCE_URL, "scope": args.scope, **totals}, ensure_ascii=False))


if __name__ == "__main__":
    main()
