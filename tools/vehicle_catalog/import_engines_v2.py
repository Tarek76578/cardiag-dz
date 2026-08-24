import argparse
import csv
import json
import os
import urllib.parse
import urllib.request
import urllib.error

SOURCE_URL = "https://github.com/gor3a/vehicle-makes-models"
BASE = os.environ["SUPABASE_URL"].rstrip("/")
KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]


def norm(v):
    return " ".join((v or "").strip().lower().replace("-", " ").split())


def n(v, kind=int):
    if v in (None, "", "null"): return None
    try: return kind(float(v))
    except (TypeError, ValueError): return None


def req(path, method="GET", body=None, query=None):
    url = BASE + "/rest/v1/" + path
    if query: url += "?" + urllib.parse.urlencode(query, doseq=True)
    data = None if body is None else json.dumps(body).encode()
    h = {"apikey": KEY, "Authorization": "Bearer " + KEY, "Content-Type": "application/json"}
    if method == "POST": h["Prefer"] = "return=representation"
    r = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(r, timeout=90) as x: return json.loads(x.read() or b"[]")
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"Supabase {e.code}: {e.read().decode(errors='replace')[:800]}")


def batch_insert(table, rows):
    for i in range(0, len(rows), 200): req(table, "POST", rows[i:i+200])


def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--scope", default="all"); args = ap.parse_args()
    path = os.environ["VEHICLE_ENGINES_CSV"]
    with open(path, encoding="utf-8-sig", newline="") as f: rows = list(csv.DictReader(f))
    if not rows: raise SystemExit("engines.csv is empty")
    expected = {"make","model","generation","gen_year_start","gen_year_end","engine_label","fuel_type","cylinders","displacement_cc","power_hp","torque_nm","transmission","drivetrain"}
    missing = expected - set(rows[0])
    if missing: raise SystemExit("Unexpected engines.csv schema: " + ",".join(sorted(missing)))

    makes = req("vehicle_makes", query={"select":"id,name"})
    models = req("vehicle_models", query={"select":"id,make_id,name"})
    gens = req("vehicle_generations", query={"select":"id,model_id,name,year_from,year_to"})
    years = req("vehicle_model_years", query={"select":"id,model_id,generation_id,model_year,market"})
    engines = req("vehicle_engines", query={"select":"id,generation_id,name"})
    make_map = {norm(x["name"]): x["id"] for x in makes}
    model_map = {(x["make_id"],norm(x["name"])): x["id"] for x in models}
    gen_map = {(x["model_id"],norm(x["name"])): x for x in gens}
    year_map = {(x["model_id"],x["generation_id"],x["model_year"],x.get("market") or "global"):x["id"] for x in years}
    eng_map = {(x["generation_id"],norm(x["name"])):x["id"] for x in engines}

    grouped = {}
    skipped = 0
    for r in rows:
        mid = model_map.get((make_map.get(norm(r["make"])),norm(r["model"])))
        if not mid: skipped += 1; continue
        key=(mid,norm(r["generation"])); grouped.setdefault(key,[]).append(r)

    new_gens=new_years=new_engines=new_links=0
    for (mid,gkey), rs in grouped.items():
        starts=[n(r["gen_year_start"]) for r in rs if n(r["gen_year_start"]) is not None]
        ends=[n(r["gen_year_end"]) for r in rs if n(r["gen_year_end"]) is not None]
        yf=min(starts) if starts else None; yt=max(ends) if ends else (max(starts) if starts else None)
        gen=gen_map.get((mid,gkey))
        if not gen:
            gen=req("vehicle_generations","POST",[{"model_id":mid,"name":rs[0]["generation"],"year_from":yf,"year_to":yt,"body_type":rs[0]["body_type"] or None,"data_status":"unverified","source_url":SOURCE_URL}])[0]
            gen_map[(mid,gkey)]=gen; new_gens+=1
        gid=gen["id"]
        if yf is not None and yt is not None and yt-yf <= 100:
            missing_years=[]
            for year in range(yf,yt+1):
                if (mid,gid,year,"global") not in year_map:
                    missing_years.append({"model_id":mid,"generation_id":gid,"model_year":year,"market":"global","data_status":"unverified","source_url":SOURCE_URL})
            if missing_years:
                made=req("vehicle_model_years","POST",missing_years)
                for x in made: year_map[(mid,gid,x["model_year"],x.get("market") or "global")]=x["id"]
                new_years += len(made)
        for r in rs:
            label=r["engine_label"].strip(); ek=(gid,norm(label)); eid=eng_map.get(ek)
            if not eid:
                created=req("vehicle_engines","POST",[{
                    "generation_id":gid,"name":label,"fuel_type":r["fuel_type"] or None,"displacement_cc":n(r["displacement_cc"]),"cylinders":n(r["cylinders"]),"power_hp":n(r["power_hp"],float),"torque_nm":n(r["torque_nm"],float),"transmission_types":[r["transmission"]] if r["transmission"] else [],"year_from":n(r["gen_year_start"]),"year_to":n(r["gen_year_end"]),"data_status":"unverified","source_url":SOURCE_URL}])
                eid=created[0]["id"]; eng_map[ek]=eid; new_engines+=1
            a=n(r["gen_year_start"]); b=n(r["gen_year_end"]) or a
            if a is not None and b is not None:
                links=[]
                for year in range(a,b+1):
                    yid=year_map.get((mid,gid,year,"global"))
                    if yid: links.append({"model_year_id":yid,"engine_id":eid,"market":"global","source_url":SOURCE_URL,"data_status":"unverified"})
                if links:
                    batch_insert("vehicle_year_engines",links); new_links+=len(links)
    print(json.dumps({"source":SOURCE_URL,"scope":args.scope,"rows":len(rows),"new_generations":new_gens,"new_years":new_years,"new_engines":new_engines,"year_engine_links":new_links,"skipped_unmatched_models":skipped},ensure_ascii=False))

if __name__ == "__main__": main()
