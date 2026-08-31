#!/usr/bin/env python3
import json
import os
import random
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from urllib.parse import urlsplit

MAX_TOKENS = int(os.getenv("AI_GATEWAY_MAX_OUTPUT_TOKENS", "12000"))
MIN_TOKENS = int(os.getenv("AI_GATEWAY_MIN_OUTPUT_TOKENS", "1024"))
TIMEOUT = int(os.getenv("AI_GATEWAY_TIMEOUT", "300"))
MAX_ATTEMPTS = int(os.getenv("AI_GATEWAY_MAX_ATTEMPTS", "2"))
BASE_COOLDOWN = float(os.getenv("AI_GATEWAY_COOLDOWN_SECONDS", "30"))
MAX_COOLDOWN = float(os.getenv("AI_GATEWAY_MAX_COOLDOWN_SECONDS", "300"))
JITTER = float(os.getenv("AI_GATEWAY_JITTER_SECONDS", "1"))

PROVIDERS = {
    "openrouter": {
        "base": os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"),
        "key": os.getenv("OPEN_ROUTER_API_KEY", ""),
        "model": os.getenv("OPENROUTER_MODEL", ""),
    },
    "groq": {
        "base": os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1"),
        "key": os.getenv("GROQ_API_KEY", ""),
        "model": os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile"),
    },
    "nvidia": {
        "base": os.getenv("NVIDIA_BASE_URL", "https://integrate.api.nvidia.com/v1"),
        "key": os.getenv("NVIDIA_API_KEY", ""),
        "model": os.getenv("NVIDIA_MODEL", "meta/llama-3.1-8b-instruct"),
    },
    "deepseek": {
        "base": os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com/v1"),
        "key": os.getenv("DEEPSEEK_API_KEY", ""),
        "model": os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
    },
    "gemini": {
        "base": os.getenv("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai"),
        "key": os.getenv("GEMINI_API_KEY", os.getenv("GEMINI_KEY", "")),
        "model": os.getenv("GEMINI_MODEL", "gemini-2.5-flash"),
    },
}

ORDER = [p.strip() for p in os.getenv("AI_GATEWAY_PROVIDER_ORDER", "openrouter,groq,nvidia,deepseek,gemini").split(",") if p.strip()]
state_lock = threading.Lock()
provider_state = {name: {"failures": 0, "cooldown_until": 0.0, "last_error": "", "success": 0} for name in PROVIDERS}
provider_locks = {name: threading.Lock() for name in PROVIDERS}


def clamp_budget(body):
    field = "max_output_tokens" if "max_output_tokens" in body else "max_tokens"
    value = body.get(field)
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        body["max_output_tokens"] = MAX_TOKENS
        return
    if value > MAX_TOKENS:
        body[field] = MAX_TOKENS


def classify(code, data):
    text = data.decode("utf-8", "replace") if isinstance(data, bytes) else str(data)
    low = text.lower()
    if code == 429 or any(x in low for x in ("rate limit", "too many requests", "rate_limit", "resource exhausted")):
        return "rate_limit"
    if code in (408, 425, 500, 502, 503, 504) or any(x in low for x in ("timeout", "overloaded", "capacity")):
        return "transient"
    if code == 401:
        return "auth"
    if code == 403:
        return "permission"
    if code == 400:
        return "invalid_request"
    return "upstream"


def retry_after(headers, body):
    raw = headers.get("Retry-After") if headers else None
    if raw:
        try:
            return max(0.0, float(raw))
        except ValueError:
            pass
    low = body.decode("utf-8", "replace").lower() if isinstance(body, bytes) else str(body).lower()
    marker = "retry after"
    if marker in low:
        tail = low.split(marker, 1)[1].strip(" :.,\"')")
        num = "".join(ch for ch in tail if ch.isdigit() or ch == ".")
        try:
            return max(0.0, float(num))
        except ValueError:
            pass
    return None


def mark_failure(provider, reason, delay=None):
    with state_lock:
        s = provider_state[provider]
        s["failures"] += 1
        s["last_error"] = reason
        if delay is None:
            delay = min(MAX_COOLDOWN, BASE_COOLDOWN * (2 ** min(s["failures"] - 1, 4)))
        s["cooldown_until"] = time.time() + delay


def mark_success(provider):
    with state_lock:
        s = provider_state[provider]
        s["success"] += 1
        s["failures"] = 0
        s["cooldown_until"] = 0.0
        s["last_error"] = ""


def available(provider):
    cfg = PROVIDERS[provider]
    if not cfg["key"]:
        return False
    with state_lock:
        return time.time() >= provider_state[provider]["cooldown_until"]


def choose_model(body, provider):
    requested = body.get("model")
    if provider == "openrouter" and requested and requested not in ("interceptor-test", ""):
        return requested
    return PROVIDERS[provider]["model"]


def candidates(requested_provider=None):
    preferred = requested_provider or os.getenv("AI_PROVIDER", "openrouter")
    ordered = [preferred] + [p for p in ORDER if p != preferred]
    return [p for p in ordered if p in PROVIDERS and available(p)]


def upstream(provider, body, accept):
    cfg = PROVIDERS[provider]
    payload = dict(body)
    payload["model"] = choose_model(body, provider)
    # Never leak the local gateway's synthetic probe model upstream.
    if payload["model"] == "interceptor-test":
        payload["model"] = cfg["model"]
    req = Request(cfg["base"].rstrip("/") + "/responses", data=json.dumps(payload, separators=(",", ":")).encode(), method="POST", headers={
        "Authorization": f"Bearer {cfg['key']}",
        "Content-Type": "application/json",
        "Accept": accept or "application/json",
        "HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz",
        "X-Title": "CarDiag Autonomous Agent",
    })
    return urlopen(req, timeout=TIMEOUT)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[gateway] " + (fmt % args), flush=True)

    def send_bytes(self, code, data, content_type="application/json"):
        if not isinstance(data, bytes):
            data = str(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", content_type or "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)
        self.wfile.flush()

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/v1/models":
            data = []
            for name in ORDER:
                if name in PROVIDERS and PROVIDERS[name]["key"]:
                    data.append({"id": PROVIDERS[name]["model"], "object": "model", "owned_by": name})
            self.send_bytes(200, json.dumps({"object": "list", "data": data}).encode())
            return
        if path == "/health":
            with state_lock:
                health = {p: dict(s) for p, s in provider_state.items()}
            self.send_bytes(200, json.dumps({"status": "ok", "providers": health}).encode())
            return
        self.send_bytes(404, b'{"error":{"message":"not found"}}')

    def do_POST(self):
        path = urlsplit(self.path).path
        if path != "/v1/responses":
            self.send_bytes(404, b'{"error":{"message":"not found"}}')
            return
        try:
            n = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(n))
            if not isinstance(body, dict):
                raise ValueError("JSON object required")
        except Exception as exc:
            self.send_bytes(400, json.dumps({"error": {"message": f"invalid JSON: {exc}"}}).encode())
            return

        clamp_budget(body)
        requested_provider = os.getenv("CODEX_PROVIDER") or os.getenv("AI_PROVIDER") or "openrouter"
        tried = []
        for provider in candidates(requested_provider):
            tried.append(provider)
            # Keep OpenRouter's current anti-inflight behavior, but scope it per provider.
            with provider_locks[provider]:
                for attempt in range(MAX_ATTEMPTS):
                    try:
                        print(f"ROUTE provider={provider} model={choose_model(body, provider)} attempt={attempt + 1}", flush=True)
                        response = upstream(provider, body, self.headers.get("Accept"))
                        with response as upstream_response:
                            mark_success(provider)
                            content_type = upstream_response.headers.get("Content-Type", "application/json")
                            self.send_response(upstream_response.status)
                            self.send_header("Content-Type", content_type)
                            self.send_header("Cache-Control", "no-cache")
                            self.send_header("Connection", "close")
                            self.send_header("Transfer-Encoding", "chunked")
                            self.end_headers()
                            while True:
                                chunk = upstream_response.read(16384)
                                if not chunk:
                                    break
                                self.wfile.write(f"{len(chunk):X}\r\n".encode())
                                self.wfile.write(chunk)
                                self.wfile.write(b"\r\n")
                                self.wfile.flush()
                            self.wfile.write(b"0\r\n\r\n")
                            self.wfile.flush()
                        return
                    except HTTPError as exc:
                        data = exc.read()
                        kind = classify(exc.code, data)
                        print(f"UPSTREAM_ERROR provider={provider} code={exc.code} class={kind}", flush=True)
                        if kind in ("auth", "permission", "invalid_request"):
                            mark_failure(provider, kind, MAX_COOLDOWN)
                            break
                        delay = retry_after(exc.headers, data)
                        if kind in ("rate_limit", "transient") and attempt + 1 < MAX_ATTEMPTS:
                            delay = delay if delay is not None else min(MAX_COOLDOWN, BASE_COOLDOWN * (attempt + 1))
                            delay += random.uniform(0, JITTER)
                            mark_failure(provider, kind, delay)
                            print(f"RECOVERY provider={provider} sleep={delay:.2f}", flush=True)
                            time.sleep(delay)
                            continue
                        mark_failure(provider, kind)
                        break
                    except (URLError, TimeoutError) as exc:
                        mark_failure(provider, "network")
                        print(f"UPSTREAM_NETWORK_ERROR provider={provider} error={type(exc).__name__}", flush=True)
                        break
                    except Exception as exc:
                        mark_failure(provider, "unexpected")
                        print(f"UPSTREAM_EXCEPTION provider={provider} error={type(exc).__name__}", flush=True)
                        break

        self.send_bytes(503, json.dumps({
            "error": {"message": "All configured AI providers failed", "type": "provider_exhausted", "providers_tried": tried}
        }).encode())


if __name__ == "__main__":
    if MAX_TOKENS <= 0 or MIN_TOKENS <= 0 or MIN_TOKENS > MAX_TOKENS:
        raise SystemExit("Invalid token budget configuration")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
