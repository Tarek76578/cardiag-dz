#!/usr/bin/env python3
"""Populate public.vehicle_images from Wikimedia Commons.

This script is deliberately conservative: it only inserts a candidate when the
Wikimedia title contains the vehicle model name. It preserves attribution and
license metadata and never overwrites existing rows.

Required environment variables:
  SUPABASE_URL
  SUPABASE_SERVICE_ROLE_KEY
"""
from __future__ import annotations

import json
import os
import re
import time
import urllib.parse
import urllib.request

SUPABASE_URL = os.environ["SUPABASE_URL"].rstrip("/")
SERVICE_KEY = os.environ["SUPABASE_SERVICE_ROLE_KEY"]
WIKI_API = "https://commons.wikimedia.org/w/api.php"
HEADERS = {
    "Authorization": f"Bearer {SERVICE_KEY}",
    "apikey": SERVICE_KEY,
    "Content-Type": "application/json",
    "User-Agent": "CarDiag-DZ/vehicle-catalog-sync (contact: github-actions)",
}


def get_json(url: str, headers: dict | None = None) -> dict:
    req = urllib.request.Request(url, headers=headers or {"User-Agent": "CarDiag-DZ/1.0"})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def supabase_get(table: str, params: str) -> list[dict]:
    url = f"{SUPABASE_URL}/rest/v1/{table}?{params}"
    data = get_json(url, HEADERS)
    if not isinstance(data, list):
        raise RuntimeError(f"Unexpected Supabase response for {table}")
    return data


def supabase_insert(row: dict) -> None:
    url = f"{SUPABASE_URL}/rest/v1/vehicle_images"
    req = urllib.request.Request(
        url,
        data=json.dumps(row).encode("utf-8"),
        headers={**HEADERS, "Prefer": "return=minimal"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        if response.status not in (200, 201, 204):
            raise RuntimeError(f"Supabase insert failed: HTTP {response.status}")


def normalize(value: str) -> str:
    value = value.lower().replace("-", " ")
    value = re.sub(r"[^a-z0-9]+", " ", value)
    return " ".join(value.split())


def tokens(value: str) -> list[str]:
    return [x for x in normalize(value).split() if len(x) >= 2]


def search_wikimedia(make: str, model: str, generation: str | None = None) -> dict | None:
    query_parts = [make, model]
    if generation:
        query_parts.append(generation)
    search = " ".join(query_parts) + " car"
    params = {
        "action": "query",
        "generator": "search",
        "gsrsearch": search,
        "gsrnamespace": "6",
        "gsrlimit": "10",
        "prop": "imageinfo",
        "iiprop": "url|extmetadata",
        "iiurlwidth": "1400",
        "format": "json",
        "formatversion": "2",
    }
    data = get_json(WIKI_API + "?" + urllib.parse.urlencode(params))
    pages = data.get("query", {}).get("pages", [])
    required = tokens(model)
    if not required:
        return None

    candidates = []
    for page in pages:
        title = page.get("title", "")
        title_norm = normalize(title)
        if not all(token in title_norm for token in required):
            continue
        info = (page.get("imageinfo") or [{}])[0]
        url = info.get("thumburl") or info.get("url")
        if not url:
            continue
        meta = info.get("extmetadata") or {}
        license_name = (meta.get("LicenseShortName") or {}).get("value")
        artist = (meta.get("Artist") or {}).get("value")
        candidates.append((0 if generation and normalize(generation) in title_norm else 1, title, url, license_name, artist, info.get("descriptionurl")))

    if not candidates:
        return None
    candidates.sort(key=lambda x: (x[0], len(x[1])))
    _, title, url, license_name, artist, source_url = candidates[0]
    return {
        "image_url": url,
        "source_url": source_url,
        "license": re.sub(r"<[^>]+>", " ", license_name or "Wikimedia Commons"),
        "author": re.sub(r"<[^>]+>", " ", artist or "Unknown"),
        "title": title,
    }


def main() -> None:
    makes = {row["id"]: row["name"] for row in supabase_get("vehicle_makes", "select=id,name")}
    models = supabase_get("vehicle_models", "select=id,make_id,name,image_url")
    generations = supabase_get("vehicle_generations", "select=id,model_id,name,year_from,year_to,image_url")
    existing = supabase_get("vehicle_images", "select=model_id,generation_id,image_url")
    existing_pairs = {(r.get("model_id"), r.get("generation_id"), r.get("image_url")) for r in existing}

    inserted = 0
    skipped = 0
    for model in models:
        make = makes.get(model["make_id"], "")
        if not make:
            skipped += 1
            continue
        gens = [g for g in generations if g.get("model_id") == model["id"]]
        candidates = [(None, model["name"], None)]
        candidates.extend((g["id"], g.get("name") or model["name"], g) for g in gens)

        for generation_id, generation_name, generation in candidates:
            # Do not replace a curated image already present in DB.
            if generation_id is None and any(r[0] == model["id"] and r[1] is None for r in existing):
                skipped += 1
                continue
            if generation_id is not None and any(r[0] == model["id"] and r[1] == generation_id for r in existing):
                skipped += 1
                continue

            result = search_wikimedia(make, model["name"], generation_name if generation_id else None)
            time.sleep(0.35)
            if not result:
                skipped += 1
                continue

            row = {
                "model_id": model["id"],
                "generation_id": generation_id,
                "image_url": result["image_url"],
                "alt_text_fr": f"{make} {model['name']} {generation_name}".strip(),
                "alt_text_ar": f"{make} {model['name']} {generation_name}".strip(),
                "is_primary": generation_id is None,
                "sort_order": 0,
                "source_url": result["source_url"],
                "license": result["license"],
                "author": result["author"],
            }
            key = (row["model_id"], row["generation_id"], row["image_url"])
            if key not in existing_pairs:
                supabase_insert(row)
                existing_pairs.add(key)
                inserted += 1
                print(f"INSERT {make} {model['name']} / {generation_name}: {result['title']}")

    print(f"Completed: inserted={inserted}, skipped={skipped}")


if __name__ == "__main__":
    main()
