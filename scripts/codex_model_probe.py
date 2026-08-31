#!/usr/bin/env python3
"""Probe a local CarDiag gateway with a Codex-shaped Responses request.

The probe intentionally uses the exact model supplied by the caller but asks for
no tool execution. A provider/model is considered viable only when the gateway
returns a valid Responses object for a request carrying Codex-style fields.
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

BASE_URL = os.getenv("CODEX_BASE_URL", "http://127.0.0.1:8787/v1").rstrip("/")
MODEL = os.environ.get("AI_GATEWAY_SELECTED_MODEL", os.environ.get("AI_MODEL", ""))
if not MODEL:
    raise SystemExit("No selected model")

tool = {
    "type": "function",
    "name": "gateway_probe",
    "description": "Return a fixed probe result",
    "parameters": {
        "type": "object",
        "properties": {"ok": {"type": "boolean"}},
        "required": ["ok"],
        "additionalProperties": False,
    },
}

payload = {
    "model": MODEL,
    "input": [{"role": "user", "content": [{"type": "input_text", "text": "Reply with exactly OK."}]}],
    "tools": [tool],
    "tool_choice": "none",
    "parallel_tool_calls": True,
    "max_output_tokens": 16,
}

req = urllib.request.Request(
    BASE_URL + "/responses",
    data=json.dumps(payload, separators=(",", ":")).encode(),
    headers={"Content-Type": "application/json", "Accept": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=90) as response:
        if response.status != 200:
            raise SystemExit(f"probe HTTP {response.status}")
        data = json.loads(response.read())
except urllib.error.HTTPError as exc:
    detail = exc.read().decode("utf-8", "replace")
    raise SystemExit(f"probe HTTP {exc.code}: {detail[:1200]}") from exc

if data.get("object") != "response":
    raise SystemExit(f"probe returned unexpected object: {data!r}")

print(f"CODEX_MODEL_PROBE=PASS model={MODEL}")
