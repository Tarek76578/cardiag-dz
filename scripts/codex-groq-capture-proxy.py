#!/usr/bin/env python3
"""Local Codex -> Groq Responses compatibility adapter.

Keeps Codex's request format compatible with Groq and makes long autonomous
runs resilient to Groq's free-tier token-per-minute ceiling. The proxy never
pretends that a model switch increases the TPM quota: it waits for the actual
reset headers, bounds completion budgets, and only falls back after a retry.
"""
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
MAX_RATE_LIMIT_RETRIES = int(os.environ.get("GROQ_RATE_LIMIT_RETRIES", "2"))
DEFAULT_RETRY_SECONDS = int(os.environ.get("GROQ_DEFAULT_RETRY_SECONDS", "45"))
MAX_OUTPUT_TOKENS = int(os.environ.get("GROQ_MAX_OUTPUT_TOKENS", "2048"))

UNSUPPORTED_REQUEST_FIELDS = {
    "previous_response_id", "store", "truncation", "include", "safety_identifier",
    "prompt_cache_key", "prompt",
}
CODEX_ONLY_FIELDS = {"client_metadata", "access_programs"}


def sanitize(value):
    if isinstance(value, dict):
        return {k: ("[REDACTED]" if k.lower() in {"authorization", "api_key", "apikey", "key", "token"} else sanitize(v)) for k, v in value.items()}
    if isinstance(value, list):
        return [sanitize(v) for v in value]
    return value


def adapt_request(parsed):
    if not isinstance(parsed, dict):
        raise ValueError("Codex request body is not a JSON object")
    adapted = dict(parsed)
    removed = []
    for key in list(adapted):
        if key in UNSUPPORTED_REQUEST_FIELDS or key in CODEX_ONLY_FIELDS:
            removed.append(key)
            adapted.pop(key, None)
    reasoning = adapted.get("reasoning")
    if isinstance(reasoning, dict) and "summary" in reasoning:
        reasoning = dict(reasoning)
        reasoning.pop("summary", None)
        if reasoning:
            adapted["reasoning"] = reasoning
        else:
            adapted.pop("reasoning", None)
        removed.append("reasoning.summary")
        print("ADAPTER_REMOVED_REASONING_SUMMARY=1", flush=True)
    tools = adapted.get("tools")
    if isinstance(tools, list):
        kept = []
        dropped = []
        for tool in tools:
            if not isinstance(tool, dict):
                continue
            if tool.get("type") == "namespace":
                dropped.append(tool.get("name", "<unnamed>"))
                continue
            kept.append(tool)
        adapted["tools"] = kept
        if dropped:
            print("ADAPTER_DROPPED_NAMESPACE_TOOLS=" + ",".join(dropped), flush=True)
    if adapted.get("tools") == []:
        adapted.pop("tools", None)
        if adapted.get("tool_choice") not in (None, "none", "auto"):
            removed.append("tool_choice")
            adapted.pop("tool_choice", None)
    return adapted, removed


def parse_duration(value):
    if not value:
        return None
    text = str(value).strip().lower()
    total = 0.0
    for number, unit in re.findall(r"([0-9]+(?:\.[0-9]+)?)\s*(ms|s|m)", text):
        n = float(number)
        total += n / 1000 if unit == "ms" else n * 60 if unit == "m" else n
    return total or None


def retry_seconds(headers, body):
    for name in ("Retry-After", "X-Ratelimit-Reset-Tokens"):
        value = headers.get(name)
        parsed = parse_duration(value)
        if parsed is not None:
            return max(1, min(300, int(parsed + 1)))
    text = body.decode("utf-8", "replace")
    match = re.search(r"try again in\s+([0-9]+(?:\.[0-9]+)?)\s*seconds", text, re.I)
    if match:
        return max(1, min(300, int(float(match.group(1)) + 1)))
    return DEFAULT_RETRY_SECONDS


def send_upstream(body, headers):
    conn = http.client.HTTPSConnection(UPSTREAM_HOST, 443, timeout=180)
    try:
        conn.request("POST", "/openai/v1/responses", body=body, headers=headers)
        response = conn.getresponse()
        return response.status, response.reason, dict(response.getheaders()), response.read()
    finally:
        conn.close()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("CAPTURE_PROXY " + (fmt % args), flush=True)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        try:
            parsed = json.loads(body)
            CAPTURE.write_text(json.dumps(sanitize(parsed), indent=2, ensure_ascii=False)[:MAX_CAPTURE], encoding="utf-8")
            adapted, removed = adapt_request(parsed)
            ADAPTED_CAPTURE.write_text(json.dumps(sanitize(adapted), indent=2, ensure_ascii=False)[:MAX_CAPTURE], encoding="utf-8")
        except Exception as exc:
            print(f"ADAPTER_REQUEST_ERROR={type(exc).__name__}: {exc}", flush=True)
            self.send_error(400, "invalid Codex request JSON")
            return

        headers = {}
        for key in ("Authorization", "Content-Type", "Accept", "OpenAI-Beta", "X-Client-Request-Id"):
            if self.headers.get(key):
                headers[key] = self.headers[key]
        headers["Host"] = UPSTREAM_HOST
        headers["Connection"] = "close"

        original_model = adapted.get("model") or PREFERRED_MODEL
        models = [original_model]
        if FALLBACK_MODEL and FALLBACK_MODEL != original_model:
            models.append(FALLBACK_MODEL)

        last_status, last_reason, last_headers, last_body = 502, "Bad Gateway", {}, b""
        for model_index, model in enumerate(models):
            if not model:
                continue
            request_obj = dict(adapted)
            request_obj["model"] = model
            # The free tier is 8K TPM for both GPT-OSS models. A large Codex
            # max_output_tokens reserves too much budget even when little text
            # is ultimately generated. Bound it to keep repeated turns viable.
            requested_output = request_obj.get("max_output_tokens")
            if isinstance(requested_output, int) and requested_output > MAX_OUTPUT_TOKENS:
                request_obj["max_output_tokens"] = MAX_OUTPUT_TOKENS
                print(f"ADAPTER_CLAMP_MAX_OUTPUT from={requested_output} to={MAX_OUTPUT_TOKENS}", flush=True)
            request_body = json.dumps(request_obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            attempts = 0
            while True:
                headers["Content-Length"] = str(len(request_body))
                print(f"GROQ_REQUEST model={model} attempt={attempts + 1} bytes={len(request_body)} max_output_tokens={request_obj.get('max_output_tokens')}", flush=True)
                try:
                    status, reason, response_headers, response_body = send_upstream(request_body, headers)
                except Exception as exc:
                    print(f"UPSTREAM_FORWARD_ERROR={type(exc).__name__}: {exc}", flush=True)
                    self.send_error(502, "capture proxy upstream error")
                    return
                last_status, last_reason, last_headers, last_body = status, reason, response_headers, response_body

                if status in (429, 500, 502, 503):
                    retry = retry_seconds(response_headers, response_body) if status == 429 else min(60, DEFAULT_RETRY_SECONDS)
                    print(f"GROQ_RETRYABLE status={status} model={model} wait={retry}s", flush=True)
                    if attempts < MAX_RATE_LIMIT_RETRIES:
                        attempts += 1
                        time.sleep(retry)
                        continue
                    if model_index + 1 < len(models):
                        print(f"GROQ_FALLBACK from={model} to={models[model_index + 1]}", flush=True)
                    break

                if status == 413:
                    # 413 is a request-size error, not a rate-limit response.
                    # Retry once with a smaller completion reservation; never
                    # blindly treat 413 as a transient 429.
                    current = request_obj.get("max_output_tokens")
                    if isinstance(current, int) and current > 1024 and attempts < MAX_RATE_LIMIT_RETRIES:
                        request_obj["max_output_tokens"] = max(512, current // 2)
                        request_body = json.dumps(request_obj, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
                        attempts += 1
                        print(f"GROQ_413_REDUCE_OUTPUT to={request_obj['max_output_tokens']}", flush=True)
                        continue
                    if model_index + 1 < len(models):
                        print(f"GROQ_413_FALLBACK from={model} to={models[model_index + 1]}", flush=True)
                    break
                break
            if last_status < 400 or model_index + 1 >= len(models):
                break

        try:
            RESPONSE_CAPTURE.write_text(last_body[:MAX_CAPTURE].decode("utf-8", "replace"), encoding="utf-8")
        except Exception:
            pass
        self.send_response(last_status, last_reason)
        hop_by_hop = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"}
        for key, value in last_headers.items():
            if key.lower() not in hop_by_hop:
                self.send_header(key, value)
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(last_body)
        self.wfile.flush()
        print(f"UPSTREAM_RESPONSE status={last_status} bytes={len(last_body)}", flush=True)
        if last_status >= 400:
            print("UPSTREAM_ERROR_BODY=" + last_body[:1200].decode("utf-8", "replace"), flush=True)


class Server(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    server = Server((HOST, PORT), Handler)
    print(f"CAPTURE_PROXY_LISTENING=http://{HOST}:{PORT}", flush=True)
    server.serve_forever()
