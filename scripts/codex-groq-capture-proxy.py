#!/usr/bin/env python3
"""Codex -> Groq Responses compatibility proxy with proactive TPM protection."""
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
MAX_OUTPUT_TOKENS = int(os.environ.get("GROQ_MAX_OUTPUT_TOKENS", "2048"))
TPM_LIMIT = int(os.environ.get("GROQ_TPM_LIMIT", "8000"))
TPM_SAFETY_MARGIN = int(os.environ.get("GROQ_TPM_SAFETY_MARGIN", "512"))
MAX_INPUT_TOKENS = int(os.environ.get("GROQ_MAX_INPUT_TOKENS", "5000"))

UNSUPPORTED_REQUEST_FIELDS = {"previous_response_id", "store", "truncation", "include", "safety_identifier", "prompt_cache_key", "prompt"}
CODEX_ONLY_FIELDS = {"client_metadata", "access_programs"}


def sanitize(v):
    if isinstance(v, dict):
        return {k: ("[REDACTED]" if k.lower() in {"authorization", "api_key", "apikey", "key", "token"} else sanitize(x)) for k, x in v.items()}
    if isinstance(v, list): return [sanitize(x) for x in v]
    return v


def adapt_request(parsed):
    if not isinstance(parsed, dict): raise ValueError("Codex request body is not a JSON object")
    a = dict(parsed); removed = []
    for k in list(a):
        if k in UNSUPPORTED_REQUEST_FIELDS or k in CODEX_ONLY_FIELDS:
            removed.append(k); a.pop(k, None)
    reasoning = a.get("reasoning")
    if isinstance(reasoning, dict) and "summary" in reasoning:
        r = dict(reasoning); r.pop("summary", None)
        if r: a["reasoning"] = r
        else: a.pop("reasoning", None)
        removed.append("reasoning.summary")
    tools = a.get("tools")
    if isinstance(tools, list):
        a["tools"] = [t for t in tools if isinstance(t, dict) and t.get("type") != "namespace"]
        if not a["tools"]: a.pop("tools", None)
    return a, removed


def header(headers, name):
    wanted = name.lower()
    for k, v in headers.items():
        if k.lower() == wanted: return v
    return None


def duration(v):
    if not v: return None
    text = str(v).strip().lower()
    m = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)\s*(ms|s|m)?", text)
    if not m: return None
    n = float(m.group(1)); unit = m.group(2) or "s"
    return n / 1000 if unit == "ms" else n * 60 if unit == "m" else n


def retry_seconds(headers, body):
    for name in ("retry-after", "x-ratelimit-reset-tokens"):
        d = duration(header(headers, name))
        if d is not None: return max(1, min(300, int(d + 1)))
    m = re.search(r"try again in\s+([0-9]+(?:\.[0-9]+)?)\s*seconds", body.decode("utf-8", "replace"), re.I)
    return max(1, min(300, int(float(m.group(1)) + 1))) if m else DEFAULT_RETRY_SECONDS


def token_estimate(body):
    # Conservative proxy estimate; actual tokenizer is provider-side.
    return max(1, (len(body) + 3) // 4)


def send(body, headers):
    c = http.client.HTTPSConnection(UPSTREAM_HOST, 443, timeout=180)
    try:
        c.request("POST", "/openai/v1/responses", body=body, headers=headers)
        r = c.getresponse(); return r.status, r.reason, dict(r.getheaders()), r.read()
    finally: c.close()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, fmt, *args): print("CAPTURE_PROXY " + (fmt % args), flush=True)

    def do_POST(self):
        raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        try:
            parsed = json.loads(raw); CAPTURE.write_text(json.dumps(sanitize(parsed), indent=2, ensure_ascii=False)[:MAX_CAPTURE], encoding="utf-8")
            adapted, removed = adapt_request(parsed)
        except Exception as e:
            self.send_error(400, "invalid Codex request JSON"); print(f"ADAPTER_REQUEST_ERROR={type(e).__name__}: {e}", flush=True); return

        headers = {k: self.headers[k] for k in ("Authorization", "Content-Type", "Accept", "OpenAI-Beta", "X-Client-Request-Id") if self.headers.get(k)}
        headers["Host"] = UPSTREAM_HOST; headers["Connection"] = "close"
        models = [adapted.get("model") or PREFERRED_MODEL]
        if FALLBACK_MODEL and FALLBACK_MODEL not in models: models.append(FALLBACK_MODEL)
        last = (502, "Bad Gateway", {}, b"")

        for mi, model in enumerate(models):
            if not model: continue
            obj = dict(adapted); obj["model"] = model
            if isinstance(obj.get("max_output_tokens"), int): obj["max_output_tokens"] = min(obj["max_output_tokens"], MAX_OUTPUT_TOKENS)
            else: obj["max_output_tokens"] = MAX_OUTPUT_TOKENS
            attempts = 0
            while True:
                body = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode()
                estimated = token_estimate(body)
                if estimated > MAX_INPUT_TOKENS:
                    # Do not fabricate a partial prompt. Ask Codex to retry with a smaller context.
                    print(f"GROQ_CONTEXT_TOO_LARGE estimated_tokens={estimated} max={MAX_INPUT_TOKENS}", flush=True)
                    self.send_response(413); self.send_header("Content-Type", "application/json"); self.send_header("Connection", "close"); self.end_headers()
                    self.wfile.write(json.dumps({"error":{"message":"Codex request context exceeds Groq free-tier proxy budget; reduce context and retry","type":"context_budget_exceeded"}}).encode()); return
                headers["Content-Length"] = str(len(body))
                print(f"GROQ_REQUEST model={model} attempt={attempts+1} estimated_tokens={estimated} max_output_tokens={obj['max_output_tokens']}", flush=True)
                try: status, reason, rh, rb = send(body, headers)
                except Exception as e:
                    self.send_error(502, "capture proxy upstream error"); print(f"UPSTREAM_FORWARD_ERROR={type(e).__name__}: {e}", flush=True); return
                last = (status, reason, rh, rb)
                remaining = header(rh, "x-ratelimit-remaining-tokens")
                reset = header(rh, "x-ratelimit-reset-tokens")
                print(f"GROQ_LIMIT remaining_tokens={remaining or 'unknown'} reset_tokens={reset or 'unknown'}", flush=True)
                if status == 429:
                    wait = retry_seconds(rh, rb)
                    if attempts < MAX_RETRIES:
                        attempts += 1; print(f"GROQ_WAIT_RATE_LIMIT seconds={wait}", flush=True); time.sleep(wait); continue
                    if mi + 1 < len(models): print(f"GROQ_FALLBACK from={model} to={models[mi+1]}", flush=True)
                    break
                if status in (500, 502, 503) and attempts < MAX_RETRIES:
                    attempts += 1; time.sleep(min(60, DEFAULT_RETRY_SECONDS * attempts)); continue
                if status == 413 and obj["max_output_tokens"] > 512 and attempts < MAX_RETRIES:
                    obj["max_output_tokens"] = max(512, obj["max_output_tokens"] // 2); attempts += 1; print(f"GROQ_413_REDUCE_OUTPUT to={obj['max_output_tokens']}", flush=True); continue
                break
            if last[0] < 400 or mi + 1 >= len(models): break

        status, reason, rh, rb = last
        RESPONSE_CAPTURE.write_text(rb[:MAX_CAPTURE].decode("utf-8", "replace"), encoding="utf-8")
        self.send_response(status, reason)
        for k, v in rh.items():
            if k.lower() not in {"connection", "keep-alive", "transfer-encoding"}: self.send_header(k, v)
        self.send_header("Connection", "close"); self.end_headers(); self.wfile.write(rb); self.wfile.flush()
        print(f"UPSTREAM_RESPONSE status={status} bytes={len(rb)}", flush=True)


class Server(ThreadingHTTPServer):
    daemon_threads = True; allow_reuse_address = True

if __name__ == "__main__":
    Server((HOST, PORT), Handler).serve_forever()
