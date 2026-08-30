#!/usr/bin/env python3
"""Codex -> Groq Responses compatibility proxy with deterministic context budgeting."""
import http.client
import json
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOST = "127.0.0.1"
PORT = int(os.environ.get("CAPTURE_PROXY_PORT", "8787"))
UPSTREAM_HOST = "api.groq.com"
CAPTURE = Path(os.environ.get("CAPTURE_FILE", "/tmp/codex-groq-request.json"))
ADAPTED_CAPTURE = Path(os.environ.get("ADAPTED_CAPTURE_FILE", "/tmp/codex-groq-adapted-request.json"))
RESPONSE_CAPTURE = Path(os.environ.get("RESPONSE_CAPTURE_FILE", "/tmp/codex-groq-response.json"))
MAX_CAPTURE = 2_000_000
PREFERRED_MODEL = os.environ.get("GROQ_PRIMARY_MODEL", "")
FALLBACK_MODEL = os.environ.get("GROQ_FALLBACK_MODEL", "openai/gpt-oss-20b")
MAX_RETRIES = int(os.environ.get("GROQ_RATE_LIMIT_RETRIES", "2"))
DEFAULT_RETRY_SECONDS = int(os.environ.get("GROQ_DEFAULT_RETRY_SECONDS", "45"))
MAX_OUTPUT_TOKENS = int(os.environ.get("GROQ_MAX_OUTPUT_TOKENS", "800"))
TPM_LIMIT = int(os.environ.get("GROQ_TPM_LIMIT", "8000"))
TPM_SAFETY_MARGIN = int(os.environ.get("GROQ_TPM_SAFETY_MARGIN", "1000"))
MAX_INPUT_TOKENS = int(os.environ.get("GROQ_MAX_INPUT_TOKENS", "4000"))
TOKEN_BYTES_PER_ESTIMATE = 3
REQUEST_OVERHEAD_TOKENS = 256
UNSUPPORTED_REQUEST_FIELDS = {"previous_response_id", "store", "truncation", "include", "safety_identifier", "prompt_cache_key", "prompt"}
CODEX_ONLY_FIELDS = {"client_metadata", "access_programs"}


def sanitize(v):
    if isinstance(v, dict):
        return {k: ("[REDACTED]" if k.lower() in {"authorization", "api_key", "apikey", "key", "token"} else sanitize(x)) for k, x in v.items()}
    if isinstance(v, list):
        return [sanitize(x) for x in v]
    return v


def token_estimate(v):
    raw = v if isinstance(v, (bytes, bytearray)) else json.dumps(v, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return max(1, (len(raw) + TOKEN_BYTES_PER_ESTIMATE - 1) // TOKEN_BYTES_PER_ESTIMATE)


def adapt_request(parsed):
    if not isinstance(parsed, dict):
        raise ValueError("Codex request body is not a JSON object")
    a = dict(parsed)
    removed = []
    for k in list(a):
        if k in UNSUPPORTED_REQUEST_FIELDS or k in CODEX_ONLY_FIELDS:
            removed.append(k)
            a.pop(k, None)
    reasoning = a.get("reasoning")
    if isinstance(reasoning, dict) and "summary" in reasoning:
        r = dict(reasoning)
        r.pop("summary", None)
        if r:
            a["reasoning"] = r
        else:
            a.pop("reasoning", None)
        removed.append("reasoning.summary")
    tools = a.get("tools")
    if isinstance(tools, list):
        kept = [t for t in tools if isinstance(t, dict) and t.get("type") != "namespace"]
        if kept:
            a["tools"] = kept
        else:
            a.pop("tools", None)
    return a, removed


def role(x):
    return x.get("role") if isinstance(x, dict) else None


def is_tool(x):
    if not isinstance(x, dict):
        return False
    t = str(x.get("type", "")).lower()
    return "tool" in t or t in {"function_call", "function_call_output", "computer_call", "computer_call_output"}


def user_item(x):
    return isinstance(x, dict) and role(x) == "user"


def trim_text(s, chars):
    if not isinstance(s, str) or len(s) <= chars:
        return s
    if chars <= 64:
        return s[:chars]
    h = chars // 2
    return s[:h] + "\n...[context compacted]...\n" + s[-(chars-h):]


def shrink(v, budget):
    if budget <= 0:
        return "[context omitted]"
    raw = json.dumps(v, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    if len(raw) <= budget:
        return v
    if isinstance(v, str):
        return trim_text(v, max(64, budget - 32))
    if isinstance(v, list):
        if not v:
            return []
        out = []
        used = 2
        for x in reversed(v):
            xb = len(json.dumps(x, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            if used + xb + 1 > budget:
                continue
            out.insert(0, x)
            used += xb + 1
        if not out:
            return [shrink(v[-1], max(128, budget - 2))]
        return out
    if isinstance(v, dict):
        out = {}
        used = 2
        keys = ["role", "type", "name", "call_id", "id", "content", "input", "arguments", "output", "text"] + list(v)
        for k in dict.fromkeys(keys):
            if k not in v:
                continue
            kr = len(json.dumps(k, ensure_ascii=False).encode()) + 3
            if used + kr >= budget:
                continue
            val = shrink(v[k], max(32, budget - used - kr))
            vr = len(json.dumps(val, ensure_ascii=False, separators=(",", ":")).encode())
            if used + kr + vr <= budget:
                out[k] = val
                used += kr + vr
        return out
    return v


def compact_input(obj, max_tokens):
    inp = obj.get("input")
    before = token_estimate(inp if inp is not None else "")
    if inp is None or before <= max_tokens:
        return dict(obj), False
    target = max(1024, max_tokens * TOKEN_BYTES_PER_ESTIMATE)
    out = dict(obj)
    if isinstance(inp, str):
        out["input"] = trim_text(inp, max(256, target - 64))
        return out, token_estimate(out["input"]) < before
    if not isinstance(inp, list):
        out["input"] = shrink(inp, target)
        return out, token_estimate(out["input"]) < before

    protected = [x for x in inp if role(x) in ("system", "developer")]
    users = [(i, x) for i, x in enumerate(inp) if user_item(x)]
    latest_user = users[-1][1] if users else None

    units = []
    i = 0
    while i < len(inp):
        x = inp[i]
        if role(x) in ("system", "developer"):
            i += 1
            continue
        if is_tool(x):
            j = i + 1
            while j < len(inp) and is_tool(inp[j]):
                j += 1
            units.append(inp[i:j])
            i = j
        else:
            units.append([x])
            i += 1

    reserved = list(protected)
    if latest_user is not None:
        reserved.append(latest_user)
    if token_estimate(reserved) > max_tokens:
        p_budget = max(256, target // 5)
        shrunk_protected = [shrink(x, max(128, p_budget // max(1, len(protected)))) for x in protected]
        used = len(json.dumps(shrunk_protected, ensure_ascii=False).encode())
        user_budget = max(256, target - used - 128)
        reserved = shrunk_protected + ([shrink(latest_user, user_budget)] if latest_user is not None else [])

    selected = []
    for unit in reversed(units):
        if latest_user is not None and any(x is latest_user for x in unit):
            continue
        trial = reserved + unit + selected
        if token_estimate(trial) <= max_tokens:
            selected = unit + selected

    candidate = reserved + selected
    if token_estimate(candidate) > max_tokens:
        candidate = shrink(candidate, target)

    if latest_user is not None and not any(user_item(x) and str(x.get("content", "")) == str(latest_user.get("content", "")) for x in candidate):
        candidate = [{"role": "developer", "content": "Previous context was compacted."}, shrink(latest_user, max(256, target - 256))]
        if protected:
            candidate.insert(0, shrink(protected[0], min(512, target // 8)))

    if token_estimate(candidate) > max_tokens:
        keep_user = next((x for x in reversed(candidate) if user_item(x)), None)
        if keep_user is not None:
            candidate = [{"role": "developer", "content": "Previous context was compacted."}, shrink(keep_user, max(128, target - 128))]
        else:
            candidate = shrink(candidate, target)

    out["input"] = candidate
    return out, token_estimate(candidate) < before


def enforce_budget(obj):
    ceiling = max(1024, TPM_LIMIT - TPM_SAFETY_MARGIN)
    out_tokens = max(256, min(int(obj.get("max_output_tokens", MAX_OUTPUT_TOKENS)), MAX_OUTPUT_TOKENS))
    out = dict(obj)
    out["max_output_tokens"] = out_tokens
    changed = False
    for _ in range(10):
        cap = max(512, min(MAX_INPUT_TOKENS, ceiling - out_tokens - REQUEST_OVERHEAD_TOKENS))
        out, did = compact_input(out, cap)
        changed = changed or did
        est = token_estimate(out.get("input", ""))
        total = est + out_tokens + REQUEST_OVERHEAD_TOKENS
        if total <= ceiling:
            return out, changed, est, total
        if out_tokens > 256:
            out_tokens = max(256, out_tokens // 2)
            out["max_output_tokens"] = out_tokens
            changed = True
            continue
        break
    est = token_estimate(out.get("input", ""))
    return out, changed, est, est + out_tokens + REQUEST_OVERHEAD_TOKENS


def header(headers, name):
    for k, v in headers.items():
        if k.lower() == name.lower():
            return v
    return None


def retry_seconds(headers, body):
    for name in ("retry-after", "x-ratelimit-reset-tokens"):
        value = header(headers, name)
        if value:
            m = re.search(r"([0-9]+(?:\.[0-9]+)?)", str(value))
            if m:
                return max(1, min(300, int(float(m.group(1)) + 1)))
    m = re.search(r"try again in\s+([0-9]+(?:\.[0-9]+)?)\s*seconds", body.decode("utf-8", "replace"), re.I)
    return max(1, min(300, int(float(m.group(1)) + 1))) if m else DEFAULT_RETRY_SECONDS


def send(body, headers):
    conn = http.client.HTTPSConnection(UPSTREAM_HOST, 443, timeout=180)
    try:
        conn.request("POST", "/openai/v1/responses", body=body, headers=headers)
        r = conn.getresponse()
        return r.status, r.reason, dict(r.getheaders()), r.read()
    finally:
        conn.close()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, fmt, *args):
        print("CAPTURE_PROXY " + (fmt % args), flush=True)

    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        try:
            parsed = json.loads(raw)
            CAPTURE.write_text(json.dumps(sanitize(parsed), indent=2, ensure_ascii=False)[:MAX_CAPTURE], encoding="utf-8")
            adapted, removed = adapt_request(parsed)
            print(f"ADAPTER_REMOVED_FIELDS={','.join(removed) if removed else 'none'}", flush=True)
        except Exception as exc:
            self.send_error(400, "invalid Codex request JSON")
            print(f"ADAPTER_REQUEST_ERROR={type(exc).__name__}: {exc}", flush=True)
            return

        headers = {k: self.headers[k] for k in ("Authorization", "Content-Type", "Accept", "OpenAI-Beta", "X-Client-Request-Id") if self.headers.get(k)}
        headers["Host"] = UPSTREAM_HOST
        headers["Connection"] = "close"
        models = [adapted.get("model") or PREFERRED_MODEL]
        if FALLBACK_MODEL and FALLBACK_MODEL not in models:
            models.append(FALLBACK_MODEL)
        last = (502, "Bad Gateway", {}, b'{"error":{"message":"proxy failure"}}')

        for mi, model in enumerate(models):
            if not model:
                continue
            obj = dict(adapted)
            attempts = 0
            while True:
                obj["model"] = model
                obj, compacted, estimated, total = enforce_budget(obj)
                ceiling = max(1024, TPM_LIMIT - TPM_SAFETY_MARGIN)
                print(f"GROQ_BUDGET model={model} input={estimated} output={obj['max_output_tokens']} total={total} ceiling={ceiling} compacted={int(compacted)}", flush=True)
                if total > ceiling:
                    print(f"GROQ_BUDGET_BLOCKED model={model} total={total} ceiling={ceiling}", flush=True)
                    if mi + 1 < len(models):
                        print(f"GROQ_FALLBACK from={model} to={models[mi+1]} reason=local_budget", flush=True)
                        break
                    last = (413, "Request Entity Too Large", {}, b'{"error":{"message":"Local Groq budget gate rejected request"}}')
                    break
                body = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                ADAPTED_CAPTURE.write_text(json.dumps(sanitize(obj), indent=2, ensure_ascii=False)[:MAX_CAPTURE], encoding="utf-8")
                headers["Content-Length"] = str(len(body))
                print(f"GROQ_REQUEST model={model} attempt={attempts + 1} input_estimate={estimated} output_budget={obj['max_output_tokens']} total_budget={total} ceiling={ceiling}", flush=True)
                try:
                    status, reason, rh, rb = send(body, headers)
                except Exception as exc:
                    self.send_error(502, "capture proxy upstream error")
                    print(f"UPSTREAM_FORWARD_ERROR={type(exc).__name__}: {exc}", flush=True)
                    return
                last = (status, reason, rh, rb)
                RESPONSE_CAPTURE.write_text(rb[:MAX_CAPTURE].decode("utf-8", "replace"), encoding="utf-8")
                print(f"UPSTREAM_RESPONSE status={status} reason={reason}", flush=True)
                if status == 429:
                    if attempts < MAX_RETRIES:
                        attempts += 1
                        wait = retry_seconds(rh, rb)
                        print(f"GROQ_WAIT_RATE_LIMIT seconds={wait}", flush=True)
                        time.sleep(wait)
                        continue
                    if mi + 1 < len(models):
                        print(f"GROQ_FALLBACK from={model} to={models[mi+1]} reason=429", flush=True)
                    break
                if status in (500, 502, 503) and attempts < MAX_RETRIES:
                    attempts += 1
                    time.sleep(min(60, DEFAULT_RETRY_SECONDS * attempts))
                    continue
                if status == 413 and attempts < MAX_RETRIES:
                    attempts += 1
                    obj["max_output_tokens"] = max(256, obj["max_output_tokens"] // 2)
                    obj, changed, _, _ = enforce_budget(obj)
                    if not changed:
                        print("GROQ_413_NO_FURTHER_COMPACTION", flush=True)
                        break
                    print(f"GROQ_413_RECOMPACT changed={int(changed)}", flush=True)
                    continue
                break
            if last[0] == 200:
                break

        status, reason, rh, rb = last
        self.send_response(status)
        for k, v in rh.items():
            if k.lower() not in {"content-length", "connection", "transfer-encoding"}:
                self.send_header(k, v)
        self.send_header("Content-Length", str(len(rb)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(rb)


if __name__ == "__main__":
    print(f"CAPTURE_PROXY_LISTENING={HOST}:{PORT}", flush=True)
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
