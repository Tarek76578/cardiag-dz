#!/usr/bin/env python3
"""Find candidate Wikimedia Commons vehicle images without guessing.

Default mode is dry-run. It emits JSON candidates with attribution metadata.
It deliberately does not download or write images; the Android app can use the
Commons file URL and the database stores the provenance.
"""
from __future__ import annotations
import argparse
import json
import re
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"

def api(params: dict) -> dict:
    query = urllib.parse.urlencode({**params, "format": "json", "origin": "*"})
    with urllib.request.urlopen(f"{API}?{query}", timeout=30) as r:
        return json.load(r)

def clean(value: str) -> str:
    return re.sub(r"\s+", " ", value or "").strip()

def search(make: str, model: str, generation: str | None = None, limit: int = 5) -> list[dict]:
    terms = [make, model]
    if generation:
        terms.append(generation)
    data = api({"action": "query", "list": "search", "srsearch": " ".join(terms), "srnamespace": 6, "srlimit": limit})
    results = []
    for item in data.get("query", {}).get("search", []):
        title = item.get("title", "")
        meta = api({"action": "query", "prop": "imageinfo", "titles": title, "iiprop": "url|extmetadata", "iiurlwidth": 1200})
        pages = meta.get("query", {}).get("pages", {})
        page = next(iter(pages.values()), {})
        info = (page.get("imageinfo") or [{}])[0]
        md = info.get("extmetadata", {})
        license_name = clean((md.get("LicenseShortName") or {}).get("value", ""))
        author = clean((md.get("Artist") or {}).get("value", ""))
        source_url = f"https://commons.wikimedia.org/wiki/{urllib.parse.quote(title.replace(' ', '_'))}"
        results.append({
            "title": title,
            "image_url": info.get("thumburl") or info.get("url"),
            "source_url": source_url,
            "license": license_name,
            "author": author,
            "attribution_required": bool(author or license_name),
        })
    return results

def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("make")
    p.add_argument("model")
    p.add_argument("--generation")
    p.add_argument("--limit", type=int, default=5)
    args = p.parse_args()
    print(json.dumps(search(args.make, args.model, args.generation, args.limit), ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
