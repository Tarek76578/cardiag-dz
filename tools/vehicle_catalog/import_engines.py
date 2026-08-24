import csv
import os
import sys
from collections import defaultdict


def norm(v):
    return " ".join((v or "").strip().lower().split())


def first(row, *names):
    for n in names:
        if n in row and row[n] not in (None, ""):
            return row[n]
    return ""


def main():
    path = os.environ.get("VEHICLE_ENGINES_CSV")
    if not path or not os.path.isfile(path):
        raise SystemExit("VEHICLE_ENGINES_CSV is missing")
    with open(path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))
    # The importer intentionally validates and prepares deterministic records here.
    # Production writes are delegated to the catalog Edge Function so service-role
    # credentials never enter generated files or logs.
    prepared = []
    for r in rows:
        make = first(r, "make", "Make", "brand", "Brand")
        model = first(r, "model", "Model")
        year_from = first(r, "year_from", "Year From", "start_year", "Start Year")
        year_to = first(r, "year_to", "Year To", "end_year", "End Year")
        engine_code = first(r, "engine_code", "Engine Code", "code", "Code")
        if not make or not model:
            continue
        prepared.append({"make": make, "model": model, "year_from": year_from, "year_to": year_to, "engine_code": engine_code})
    print(f"Prepared {len(prepared)} source engine records")
    print("Next stage: call import-vehicle-catalog Edge Function with validated batches.")


if __name__ == "__main__":
    main()
