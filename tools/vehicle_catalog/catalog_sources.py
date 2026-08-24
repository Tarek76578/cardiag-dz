"""Canonical source metadata for CarDiag vehicle catalog ingestion.

Sources are separated by responsibility. This file contains no API keys.
"""

SOURCES = {
    "vpic": {
        "name": "NHTSA vPIC",
        "base_url": "https://vpic.nhtsa.dot.gov/api/",
        "role": "vehicle identity and VIN-oriented reference data",
        "commercial_use": True,
    },
    "wikimedia": {
        "name": "Wikimedia Commons",
        "api_url": "https://commons.wikimedia.org/w/api.php",
        "role": "vehicle images with per-file license/author attribution",
        "commercial_use": "license-dependent",
    },
    "api_ninjas": {
        "name": "API Ninjas Cars",
        "base_url": "https://api.api-ninjas.com/v1/cars",
        "role": "optional commercial enrichment for vehicle specifications",
        "commercial_use": "paid plan / contract dependent",
    },
}


def source_record(source_key: str) -> dict:
    source = SOURCES[source_key]
    return {"key": source_key, **source}
