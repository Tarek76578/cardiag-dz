import csv
import json
import os
import urllib.request

BATCH_SIZE = 400
SOURCE_URL = "https://github.com/gor3a/vehicle-makes-models"


def first(row, *names):
    for n in names:
        if n in row and row[n] not in (None, ""):
            return row[n]
    return ""


def main():
    path = os.environ.get("VEHICLE_ENGINES_CSV")
    supabase_url = os.environ.get("SUPABASE_URL", "").rstrip("/")
    service_key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not path or not os.path.isfile(path):
        raise SystemExit("VEHICLE_ENGINES_CSV is missing")
    if not supabase_url or not service_key:
        raise SystemExit("SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are required")

    with open(path, encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        rows = []
        for r in reader:
            make = first(r, "make")
            model = first(r, "model")
            generation = first(r, "generation")
            if not make or not model or not generation:
                continue
            rows.append({
                "make": make,
                "model": model,
                "generation": generation,
                "gen_year_start": first(r, "gen_year_start"),
                "gen_year_end": first(r, "gen_year_end"),
                "body_type": first(r, "body_type"),
                "engine_label": first(r, "engine_label"),
                "fuel_type": first(r, "fuel_type"),
                "cylinders": first(r, "cylinders"),
                "displacement_cc": first(r, "displacement_cc"),
                "power_hp": first(r, "power_hp"),
                "torque_nm": first(r, "torque_nm"),
                "transmission": first(r, "transmission"),
                "drivetrain": first(r, "drivetrain"),
            })

    endpoint = f"{supabase_url}/functions/v1/import-vehicle-catalog-v2"
    total = 0
    linked = 0
    for start in range(0, len(rows), BATCH_SIZE):
        batch = rows[start:start + BATCH_SIZE]
        payload = json.dumps({"records": batch}).encode("utf-8")
        req = urllib.request.Request(
            endpoint,
            data=payload,
            method="POST",
            headers={
                "Authorization": f"Bearer {service_key}",
                "Content-Type": "application/json",
            },
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            result = json.loads(resp.read().decode("utf-8"))
        if not result.get("ok"):
            raise SystemExit(f"Supabase import failed at batch {start}: {result}")
        total += int(result.get("imported", 0))
        linked += int(result.get("yearsLinked", 0))
        print(f"batch={start // BATCH_SIZE + 1} rows={len(batch)} imported={result.get('imported', 0)} yearsLinked={result.get('yearsLinked', 0)}")

    print(f"Imported {total} engine records; linked {linked} year-engine records; source={SOURCE_URL}")


if __name__ == "__main__":
    main()
