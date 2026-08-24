import argparse
import json
import os
import urllib.request

SOURCE_URL = "https://github.com/gor3a/vehicle-makes-models"
TOTAL_SOURCE_ROWS = 30390
BATCH_SIZE = 25


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--scope", default="all")
    args = ap.parse_args()
    base = os.environ["SUPABASE_URL"].rstrip("/")
    key = os.environ["SUPABASE_ANON_KEY"]
    endpoint = f"{base}/functions/v1/vehicle-catalog-import-v5"
    total_written = 0
    total_failed = 0
    for start in range(0, TOTAL_SOURCE_ROWS, BATCH_SIZE):
        payload = json.dumps({"start": start, "limit": BATCH_SIZE}).encode()
        req = urllib.request.Request(endpoint, data=payload, method="POST", headers={"Authorization": f"Bearer {key}", "apikey": key, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=180) as response:
                result = json.loads(response.read().decode())
        except Exception as exc:
            raise SystemExit(f"Canonical importer failed at source offset {start}: {exc}")
        if not result.get("ok"):
            raise SystemExit(f"Canonical importer failed at source offset {start}: {result}")
        written = int(result.get("written", 0)); failed = int(result.get("failed", 0))
        total_written += written; total_failed += failed
        print(f"batch={(start // BATCH_SIZE) + 1} start={start} processed={result.get('processed',0)} written={written} failed={failed} total={result.get('total',0)}", flush=True)
    if total_written == 0:
        raise SystemExit("Canonical importer completed without writing records")
    print(json.dumps({"source": SOURCE_URL, "scope": args.scope, "source_rows": TOTAL_SOURCE_ROWS, "written": total_written, "failed": total_failed}, ensure_ascii=False))


if __name__ == "__main__":
    main()
