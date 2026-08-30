#!/usr/bin/env python3
"""Codex -> Groq Responses compatibility proxy with hard TPM/context enforcement."""
import http.client, json, os, re, time
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


def item_role(item):
    return item.get("role") if isinstance(item, dict) else None


def is_tool_item(item):
    if not isinstance(item, dict):
        return False
    t = str(item.get("type", "")).lower()
    return "tool" in t or t in {"function_call", "function_call_output", "computer_call", "computer_call_output"}


def trim_text(s, max_chars):
    if not isinstance(s, str) or len(s) <= max_chars:
        return s
    if max_chars <= 32:
        return s[:max_chars]
    head = max_chars // 2
    tail = max_chars - head
    return s[:head] + "\n...[context compacted]...\n" + s[-tail:]


def shrink_value(v, budget_bytes):
    """Deterministically shrink a value. Returns (value, changed)."""
    if budget_bytes <= 0:
        return "[context omitted]", True
    raw_len = len(json.dumps(v, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
    if raw_len <= budget_bytes:
        return v, False
    if isinstance(v, str):
        return trim_text(v, max(32, budget_bytes - 32)), True
    if isinstance(v, list):
        out = []
        used = 2
        for x in reversed(v):
            xraw = len(json.dumps(x, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            if used + xraw + 1 > budget_bytes:
                continue
            out.insert(0, x)
            used += xraw + 1
        if not out and v:
            return [shrink_value(v[-1], max(64, budget_bytes - 2))[0]], True
        return out, True
    if isinstance(v, dict):
        out = {}
        used = 2
        priority = ["role", "type", "name", "call_id", "id", "content", "input", "arguments", "output", "text"]
        keys = priority + [k for k in v if k not in priority]
        for k in keys:
            if k not in v:
                continue
            kr = len(json.dumps(k, ensure_ascii=False).encode("utf-8")) + 3
            if used + kr >= budget_bytes:
                continue
            remaining = budget_bytes - used - kr
            val, _ = shrink_value(v[k], max(32, remaining))
            vr = len(json.dumps(val, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            if used + kr + vr > budget_bytes:
                continue
            out[k] = val
            used += kr + vr
        return out, True
    return v, True


def compact_input(obj, max_input_tokens):
    """Guarantee input is <= max_input_tokens when structurally possible."""
    inp = obj.get("input")
    if inp is None:
        return obj, False
    target_bytes = max(1024, max_input_tokens * TOKEN_BYTES_PER_ESTIMATE)
    before = token_estimate(inp)
    if before <= max_input_tokens:
        return obj, False

    if isinstance(inp, str):
        new_inp = trim_text(inp, max(256, target_bytes - 64))
    elif isinstance(inp, list):
        protected = []
        units = []
        i = 0
        while i < len(inp):
            x = inp[i]
            if item_role(x) in ("system", "developer"):
                protected.append(x)
                i += 1
                continue
            if is_tool_item(x):
                unit = [x]
                j = i + 1
                while j < len(inp) and is_tool_item(inp[j]):
                    unit.append(inp[j])
                    j += 1
                units.append(unit)
                i = j
            else:
                units.append([x])
                i += 1
        selected = []
        # Always retain newest complete units first.
        for unit in reversed(units):
            trial = protected + unit + selected
            if token_estimate(trial) <= max_input_tokens:
                selected = unit + selected
            else:
                break
        new_inp = protected + selected
        if token_estimate(new_inp) > max_input_tokens:
            # Protected instructions themselves can be oversized. Shrink them while preserving order.
            p_budget = max(512, target_bytes // 3)
            shrunk = []
            for x in protected:
                y, _ = shrink_value(x, max(128, p_budget // max(1, len(protected))))
                shrunk.append(y)
            new_inp = shrunk + selected
        if token_estimate(new_inp) > max_input_tokens:
            # Last-resort deterministic shrink of the whole input. This is only reached for a huge
            # single item; tool items remain grouped, and the resulting JSON stays valid.
            new_inp, _ = shrink_value(new_inp, target_bytes)
    else:
        new_inp, _ = shrink_value(inp, target_bytes)

    new = dict(obj)
    new["input"] = new_inp
    after = token_estimate(new_inp)
    if after >= before:
        # Absolute emergency: replace history with a compact marker plus the newest user-like item.
        if isinstance(inp, list):
            newest = inp[-1] if inp else {"role": "user", "content": "Continue the task."}
            newest, _ = shrink_value(newest, max(256, target_bytes - 128))
            new["input"] = [{"role": "developer", "content": "Previous context was compacted."}, newest]
        else:
            new["input"] = trim_text(str(inp), max(256, target_bytes - 128))
        after = token_estimate(new["input"])
    return new, after < before


def enforce_budget(obj):
    """Return (object, changed, estimated_input, total_budget); never knowingly exceeds the local gate."""
    ceiling = max(1024, TPM_LIMIT - TPM_SAFETY_MARGIN)
    out = max(256, min(int(obj.get("max_output_tokens", MAX_OUTPUT_TOKENS)), MAX_OUTPUT_TOKENS))
    obj = dict(obj)
    obj["max_output_tokens"] = out
    cap = max(512, min(MAX_INPUT_TOKENS, ceiling - out - REQUEST_OVERHEAD_TOKENS))
    changed_any = False
    for _ in range(6):
        obj, changed = compact_input(obj, cap)
        changed_any = changed_any or changed
        est = token_estimate(obj.get("input", ""))
        total = est + out + REQUEST_OVERHEAD_TOKENS
        if total <= ceiling:
            return obj, changed_any, est, total
        if out > 256:
            out = max(256, out // 2)
            obj["max_output_tokens"] = out
            cap = max(512, min(MAX_INPUT_TOKENS, ceiling - out - REQUEST_OVERHEAD_TOKENS))
            changed_any = True
            continue
        cap = max(512, min(cap - 256, ceiling - out - REQUEST_OVERHEAD_TOKENS))
        if cap < 512:
            break
    # Hard final gate. Do not send an oversized request.
    obj, changed = compact_input(obj, max(512, ceiling - out - REQUEST_OVERHEAD_TOKENS))
    changed_any = changed_any or changed
    est = token_estimate(obj.get("input", ""))
    total = est + out + REQUEST_OVERHEAD_TOKENS
    return obj, changed_any, est, total


def header(headers, name):
    n = name.lower()
    for k, v in headers.items():
        if k.lower() == n:
            return v
    return None


def duration(v):
    if not v:
        return None
    m = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)\s*(ms|s|m)?", str(v).strip().lower())
    if not m:
        return None
    n = float(m.group(1)); u = m.group(2) or "s"
    return n / 1000 if u == "ms" else n * 60 if u == "m" else n


def retry_seconds(headers, body):
    for n in ("retry-after", "x-ratelimit-reset-tokens"):
        d = duration(header(headers, n))
        if d is not None:
            return max(1, min(300, int(d + 1)))
    m = re.search(r"try again in\s+([0-9]+(?:\.[0-9]+)?)\s*seconds", body.decode("utf-8", "replace"), re.I)
    return max(1, min(300, int(float(m.group(1)) + 1))) if m else DEFAULT_RETRY_SECONDS


def send(body, headers):
    c = http.client.HTTPSConnection(UPSTREAM_HOST, 443, timeout=180)
    try:
        c.request("POST", "/openai/v1/responses", body=body, headers=headers)
        r = c.getresponse()
        return r.status, r.reason, dict(r.getheaders()), r.read()
    finally:
        c.close()


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
        except Exception as e:
            self.send_error(400, "invalid Codex request JSON")
            print(f"ADAPTER_REQUEST_ERROR={type(e).__name__}: {e}", flush=True)
            return

        headers = {k: self.headers[k] for k in ("Authorization", "Content-Type", "Accept", "OpenAI-Beta", "X-Client-Request-Id") if self.headers.get(k)}
        headers["Host"] = UPSTREAM_HOST
        headers["Connection"] = "close"
        models = [adapted.get("model") or PREFERRED_MODEL]
        if FALLBACK_MODEL and FALLBACK_MODEL not in models:
            models.append(FALLBACK_MODEL)
        last = (502, "Bad Gateway", {}, b"")

        for mi, model in enumerate(models):
            if not model:
                continue
            obj = dict(adapted)
            attempts = 0
            while True:
                obj, compacted, estimated, total = enforce_budget(obj)
                ceiling = max(1024, TPM_LIMIT - TPM_SAFETY_MARGIN)
                if total > ceiling:
                    print(f"GROQ_BUDGET_BLOCKED model={model} estimated={estimated} output={obj.get('max_output_tokens')} total={total} ceiling={ceiling}", flush=True)
                    if mi + 1 < len(models):
                        print(f"GROQ_FALLBACK from={model} to={models[mi+1]} reason=local_budget", flush=True)
                        break
                    last = (413, "Request Entity Too Large", {}, b'{"error":{"message":"Local Groq budget gate could not compact request"}}')
                    break
                body = json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                ADAPTED_CAPTURE.write_text(json.dumps(sanitize(obj), indent=2, ensure_ascii=False)[:MAX_CAPTURE], encoding="utf-8")
                headers["Content-Length"] = str(len(body))
                print(f"GROQ_REQUEST model={model} attempt={attempts+1} input_estimate={estimated} output_budget={obj['max_output_tokens']} total_budget={total} ceiling={ceiling}", flush=True)
                try:
                    status, reason, rh, rb = send(body, headers)
                except Exception as e:
                    self.send_error(502, "capture proxy upstream error")
                    print(f"UPSTREAM_FORWARD_ERROR={type(e).__name__}: {e}", flush=True)
                    return
                last = (status, reason, rh, rb)
                remaining = header(rh, "x-ratelimit-remaining-tokens"); reset = header(rh, "x-ratelimit-reset-tokens")
                print(f"GROQ_LIMIT remaining_tokens={remaining or 'unknown'} reset_tokens={reset or 'unknown'}", flush=True)
                if status == 429:
                    wait = retry_seconds(rh, rb)
                    if attempts < MAX_RETRIES:
                        attempts += 1
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
                if status == 413:
                    if attempts < MAX_RETRIES:
                        attempts += 1
                        # Server-side tokenization can exceed the conservative estimate. Force another
                        # compaction and lower output before trying again.
                        obj["max_output_tokens"] = max(256, obj["max_output_tokens"] // 2)
                        obj, changed, estimated2, total2 = enforce_budget(obj)
                        print(f"GROQ_413_RECOVERY attempt={attempts} output={obj['max_output_tokens']} input_estimate={estimated2} total={total2} changed={int(changed)}", flush=True)
                        continue
                    if mi + 1 < len(models):
                        print(f"GROQ_413_FALLBACK from={model} to={models[mi+1]} reason=413", flush=True)
                    break
                break
            if last[0] < 400 or mi + 1 >= len(models):
                break

        status, reason, rh, rb = last
        RESPONSE_CAPTURE.write_text(rb[:MAX_CAPTURE].decode("utf-8", "replace"), encoding="utf-8")
        self.send_response(status, reason)
        for k, v in rh.items():
            if k.lower() not in {"connection", "keep-alive", "transfer-encoding"}:
                self.send_header(k, v)
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(rb)
        self.wfile.flush()
        print(f"UPSTREAM_RESPONSE status={status} bytes={len(rb)}", flush=True)


class Server(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    print(f"CAPTURE_PROXY_LISTENING={HOST}:{PORT}", flush=True)
    Server((HOST, PORT), Handler).serve_forever()
