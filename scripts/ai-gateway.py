#!/usr/bin/env python3
"""CarDiag AI Gateway.

Independent implementation inspired by the public architecture ideas in the MIT-
licensed Free Claude Code project: failure classification, bounded retries,
provider admission, cooldown/recovery and controlled fallback.
"""
from __future__ import annotations

import json
import os
import random
import threading
import time
from collections import deque
from dataclasses import dataclass, field
from email.utils import parsedate_to_datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from urllib.parse import urlsplit

MAX_TOKENS = int(os.getenv("AI_GATEWAY_MAX_OUTPUT_TOKENS", "12000"))
MIN_TOKENS = int(os.getenv("AI_GATEWAY_MIN_OUTPUT_TOKENS", "1024"))
TIMEOUT = int(os.getenv("AI_GATEWAY_TIMEOUT", "300"))
MAX_ATTEMPTS = int(os.getenv("AI_GATEWAY_MAX_ATTEMPTS", "5"))
BASE_COOLDOWN = float(os.getenv("AI_GATEWAY_COOLDOWN_SECONDS", "30"))
MAX_COOLDOWN = float(os.getenv("AI_GATEWAY_MAX_COOLDOWN_SECONDS", "300"))
JITTER = float(os.getenv("AI_GATEWAY_JITTER_SECONDS", "1"))
RATE_LIMIT = int(os.getenv("AI_GATEWAY_RATE_LIMIT", "20"))
RATE_WINDOW = float(os.getenv("AI_GATEWAY_RATE_WINDOW_SECONDS", "60"))
MAX_CONCURRENCY = int(os.getenv("AI_GATEWAY_MAX_CONCURRENCY", "3"))

DEFAULT_PROVIDER_ORDER = "openrouter,groq,deepseek"
ORDER = [x.strip() for x in os.getenv("AI_GATEWAY_PROVIDER_ORDER", DEFAULT_PROVIDER_ORDER).split(",") if x.strip()]

PROVIDERS: dict[str, dict[str, Any]] = {
    "openrouter": {
        "base": os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"),
        "key": os.getenv("OPEN_ROUTER_API_KEY", ""),
        "model": os.getenv("OPENROUTER_MODEL", ""),
        "models": os.getenv("OPENROUTER_FALLBACK_MODELS", "openai/gpt-oss-120b:free,openai/gpt-oss-20b:free,qwen/qwen3-coder:free,deepseek/deepseek-chat-v3.1:free"),
        "codex": True,
    },
    "groq": {
        "base": os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1"),
        "key": os.getenv("GROQ_API_KEY", ""),
        "model": os.getenv("GROQ_MODEL", "openai/gpt-oss-120b"),
        "models": os.getenv("GROQ_FALLBACK_MODELS", "openai/gpt-oss-120b,openai/gpt-oss-20b,llama-3.3-70b-versatile"),
        "codex": True,
    },
    "deepseek": {
        "base": os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
        "key": os.getenv("DEEPSEEK_API_KEY", ""),
        "model": os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
        "models": os.getenv("DEEPSEEK_FALLBACK_MODELS", "deepseek-chat,deepseek-reasoner"),
        "codex": True,
    },
}

state_lock = threading.RLock()
provider_state: dict[str, "ProviderState"] = {}
provider_semaphores = {name: threading.BoundedSemaphore(MAX_CONCURRENCY) for name in PROVIDERS}

@dataclass
class ProviderState:
    failures: int = 0
    successes: int = 0
    cooldown_until: float = 0.0
    last_error: str = ""
    recovery_generation: int = 0
    recovery_ready_at: float = 0.0
    recovery_leader: bool = False
    recent_calls: deque[float] = field(default_factory=deque)
    discovered_models: list[str] = field(default_factory=list)

for _name in PROVIDERS:
    provider_state[_name] = ProviderState()


def body_text(data: Any) -> str:
    if isinstance(data, bytes):
        return data.decode("utf-8", "replace")
    if isinstance(data, str):
        return data
    return json.dumps(data, ensure_ascii=False, default=str)


def classify(code: int | None, data: bytes | str) -> str:
    low = body_text(data).lower()
    if code == 429 or any(s in low for s in ("rate_limit", "rate limit", "too many requests", "resource exhausted")):
        return "rate_limit"
    if code in (408, 425, 500, 502, 503, 504) or any(s in low for s in ("timeout", "overloaded", "capacity", "temporarily unavailable")):
        return "transient"
    if code == 401 or any(s in low for s in ("invalid api key", "authentication failed", "unauthorized")):
        return "auth"
    if code == 402 or any(s in low for s in ("payment required", "billing", "insufficient credits", "credits required")):
        return "billing"
    if code == 403 or any(s in low for s in ("permission denied", "forbidden")):
        return "permission"
    if code == 404 or any(s in low for s in ("model not found", "not found")):
        return "not_found"
    if code == 413 or any(s in low for s in ("context length", "too large")):
        return "request_too_large"
    if code == 400:
        return "invalid_request"
    return "upstream"


def retry_after(headers: Any, data: bytes | str) -> float | None:
    raw = headers.get("Retry-After") if headers else None
    if raw:
        try:
            return max(0.0, float(raw))
        except (TypeError, ValueError):
            try:
                return max(0.0, parsedate_to_datetime(raw).timestamp() - time.time())
            except Exception:
                pass
    low = body_text(data).lower()
    marker = "retry after"
    if marker in low:
        tail = low.split(marker, 1)[1].strip(" :.,\"')")
        digits = ""
        for char in tail:
            if char.isdigit() or char == ".":
                digits += char
            elif digits:
                break
        if digits:
            try:
                return max(0.0, float(digits))
            except ValueError:
                pass
    return None


def clamp_budget(body: dict[str, Any]) -> None:
    field = "max_output_tokens" if "max_output_tokens" in body else "max_tokens"
    value = body.get(field)
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        body["max_output_tokens"] = MAX_TOKENS
        print(f"TOKEN_INJECT field=max_output_tokens effective={MAX_TOKENS}", flush=True)
    elif value > MAX_TOKENS:
        body[field] = MAX_TOKENS
        print(f"TOKEN_CLAMP field={field} original={value} effective={MAX_TOKENS}", flush=True)


def configured_models(provider: str) -> list[str]:
    return [x.strip() for x in str(PROVIDERS[provider]["models"]).split(",") if x.strip()]


def discover_models(provider: str) -> list[str]:
    cfg = PROVIDERS[provider]
    if not cfg["key"]:
        return []
    try:
        req = Request(cfg["base"].rstrip("/") + "/models", headers={"Authorization": f"Bearer {cfg['key']}", "Accept": "application/json"})
        with urlopen(req, timeout=min(20, TIMEOUT)) as response:
            payload = json.loads(response.read().decode("utf-8", "replace"))
        discovered = [str(item["id"]) for item in payload.get("data", []) if isinstance(item, dict) and item.get("id")]
    except Exception as exc:
        print(f"MODEL_DISCOVERY_ERROR provider={provider} error={type(exc).__name__}", flush=True)
        return []
    preferred = configured_models(provider)
    discovered.sort(key=lambda model: (preferred.index(model) if model in preferred else len(preferred), model))
    with state_lock:
        provider_state[provider].discovered_models = discovered[:50]
    print(f"MODEL_DISCOVERY provider={provider} count={len(discovered)}", flush=True)
    return discovered


def model_options(provider: str, requested: str | None) -> list[str]:
    options: list[str] = []
    preferred = PROVIDERS[provider]["model"]
    configured = configured_models(provider)
    if requested and requested not in ("", "interceptor-test") and (provider == "openrouter" or requested in configured):
        options.append(requested)
    if preferred and preferred not in options:
        options.append(preferred)
    for value in configured:
        if value not in options:
            options.append(value)
    with state_lock:
        discovered = list(provider_state[provider].discovered_models)
    if len(options) <= 1:
        for value in discover_models(provider):
            if value not in options:
                options.append(value)
    else:
        for value in discovered:
            if value not in options:
                options.append(value)
    return options


def provider_ready(provider: str, codex_request: bool) -> bool:
    cfg = PROVIDERS[provider]
    if not cfg["key"] or not cfg["model"]:
        return False
    if codex_request and not cfg["codex"]:
        return False
    with state_lock:
        return time.monotonic() >= provider_state[provider].cooldown_until


def acquire_rate_slot(provider: str) -> None:
    while True:
        now = time.monotonic()
        with state_lock:
            calls = provider_state[provider].recent_calls
            while calls and now - calls[0] >= RATE_WINDOW:
                calls.popleft()
            if len(calls) < RATE_LIMIT:
                calls.append(now)
                return
            delay = max(0.05, RATE_WINDOW - (now - calls[0]))
        time.sleep(delay)


def start_recovery(provider: str, delay: float) -> None:
    with state_lock:
        state = provider_state[provider]
        ready_at = time.monotonic() + max(0.0, delay)
        if state.recovery_ready_at <= time.monotonic():
            state.recovery_generation += 1
            state.recovery_leader = True
            state.recovery_ready_at = ready_at
        state.cooldown_until = max(state.cooldown_until, ready_at)


def mark_failure(provider: str, reason: str, delay: float | None = None) -> None:
    with state_lock:
        state = provider_state[provider]
        state.failures += 1
        state.last_error = reason
        state.recovery_generation += 1
        if delay is None:
            delay = min(MAX_COOLDOWN, BASE_COOLDOWN * (2 ** min(state.failures - 1, 4)))
        state.cooldown_until = time.monotonic() + max(0.0, delay)
        state.recovery_ready_at = state.cooldown_until
        state.recovery_leader = True


def mark_success(provider: str) -> None:
    with state_lock:
        state = provider_state[provider]
        state.successes += 1
        state.failures = 0
        state.last_error = ""
        state.cooldown_until = 0.0
        state.recovery_ready_at = 0.0
        state.recovery_leader = False


def candidate_providers(requested_provider: str, codex_request: bool) -> list[str]:
    preferred = requested_provider if requested_provider in PROVIDERS else "openrouter"
    ordered = [preferred] + [x for x in ORDER if x != preferred]
    return [p for p in ordered if provider_ready(p, codex_request)]


def build_payload(provider: str, body: dict[str, Any], model: str) -> tuple[str, dict[str, Any]]:
    payload = dict(body)
    payload["model"] = model
    return PROVIDERS[provider]["base"].rstrip("/") + "/responses", payload


def upstream_call(provider: str, body: dict[str, Any], model: str, accept: str | None):
    url, payload = build_payload(provider, body, model)
    headers = {
        "Authorization": f"Bearer {PROVIDERS[provider]['key']}",
        "Content-Type": "application/json",
        "Accept": accept or "application/json",
    }
    if provider == "openrouter":
        headers.update({"HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz", "X-Title": "CarDiag Autonomous Agent"})
    return urlopen(Request(url, data=json.dumps(payload, separators=(",", ":")).encode(), method="POST", headers=headers), timeout=TIMEOUT)


def forward_response(handler: BaseHTTPRequestHandler, upstream: Any) -> None:
    with upstream as response:
        content_type = response.headers.get("Content-Type", "application/json")
        handler.send_response(response.status)
        handler.send_header("Content-Type", content_type)
        handler.send_header("Cache-Control", "no-cache")
        handler.send_header("Connection", "close")
        handler.send_header("Transfer-Encoding", "chunked")
        handler.end_headers()
        while True:
            chunk = response.read(16384)
            if not chunk:
                break
            handler.wfile.write(f"{len(chunk):X}\r\n".encode())
            handler.wfile.write(chunk)
            handler.wfile.write(b"\r\n")
            handler.wfile.flush()
        handler.wfile.write(b"0\r\n\r\n")
        handler.wfile.flush()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[gateway] " + (fmt % args), flush=True)

    def send_json(self, code: int, value: dict[str, Any]) -> None:
        data = json.dumps(value, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)
        self.wfile.flush()

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/health":
            with state_lock:
                providers = {
                    name: {
                        "configured": bool(cfg["key"]),
                        "codex_compatible": bool(cfg["codex"]),
                        "model": cfg["model"],
                        "failures": provider_state[name].failures,
                        "successes": provider_state[name].successes,
                        "cooldown_remaining": max(0.0, provider_state[name].cooldown_until - time.monotonic()),
                        "last_error": provider_state[name].last_error,
                        "recovery_generation": provider_state[name].recovery_generation,
                        "discovered_models": len(provider_state[name].discovered_models),
                    }
                    for name in PROVIDERS
                }
            self.send_json(200, {"status": "ok", "providers": providers})
            return
        if path == "/v1/models":
            data = []
            for provider in ORDER:
                if provider in PROVIDERS and provider_ready(provider, True):
                    data.append({"id": model_for(provider, None), "object": "model", "owned_by": provider})
            self.send_json(200, {"object": "list", "data": data})
            return
        self.send_json(404, {"error": {"message": "not found"}})

    def do_POST(self):
        if urlsplit(self.path).path != "/v1/responses":
            self.send_json(404, {"error": {"message": "not found"}})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length))
            if not isinstance(body, dict):
                raise ValueError("JSON object required")
        except Exception as exc:
            self.send_json(400, {"error": {"message": f"invalid JSON: {exc}"}})
            return

        clamp_budget(body)
        requested_provider = os.getenv("AI_PROVIDER", "openrouter")
        requested_model = body.get("model")
        codex_request = os.getenv("CODEX_PROVIDER") == "cardiag_gateway"
        tried: list[str] = []

        for provider in candidate_providers(requested_provider, codex_request):
            with provider_semaphores[provider]:
                if not provider_ready(provider, codex_request):
                    continue
                options = model_options(provider, requested_model)
                if not options:
                    continue
                attempts = 0
                for model in options:
                    if attempts >= MAX_ATTEMPTS:
                        break
                    attempts += 1
                    tried.append(f"{provider}/{model}")
                    acquire_rate_slot(provider)
                    print(f"ROUTE provider={provider} model={model} attempt={attempts}/{MAX_ATTEMPTS}", flush=True)
                    try:
                        response = upstream_call(provider, body, model, self.headers.get("Accept"))
                        forward_response(self, response)
                        mark_success(provider)
                        return
                    except HTTPError as exc:
                        data = exc.read()
                        kind = classify(exc.code, data)
                        print(f"UPSTREAM_ERROR provider={provider} model={model} code={exc.code} class={kind}", flush=True)
                        if kind == "not_found":
                            continue
                        if kind in ("auth", "billing", "invalid_request", "request_too_large"):
                            mark_failure(provider, kind, MAX_COOLDOWN)
                            break
                        delay = retry_after(exc.headers, data)
                        if kind in ("rate_limit", "transient"):
                            if attempts < MAX_ATTEMPTS:
                                delay = delay if delay is not None else min(MAX_COOLDOWN, BASE_COOLDOWN * attempts)
                                delay += random.uniform(0.0, JITTER)
                                mark_failure(provider, kind, delay)
                                print(f"RECOVERY provider={provider} generation={provider_state[provider].recovery_generation} sleep={delay:.2f}", flush=True)
                                time.sleep(delay)
                                continue
                        mark_failure(provider, kind)
                        break
                    except (URLError, TimeoutError) as exc:
                        mark_failure(provider, "network")
                        print(f"UPSTREAM_NETWORK_ERROR provider={provider} model={model} error={type(exc).__name__}", flush=True)
                        break
                    except (BrokenPipeError, ConnectionResetError):
                        return
                    except Exception as exc:
                        mark_failure(provider, "unexpected")
                        print(f"UPSTREAM_EXCEPTION provider={provider} model={model} error={type(exc).__name__}", flush=True)
                        break

        self.send_json(503, {"error": {"message": "All compatible AI providers failed", "type": "provider_exhausted", "providers_tried": tried}})


def model_for(provider: str, requested: str | None) -> str:
    cfg = PROVIDERS[provider]
    candidates = configured_models(provider)
    if requested and requested not in ("", "interceptor-test") and (provider == "openrouter" or requested in candidates):
        return requested
    return cfg["model"] or (candidates[0] if candidates else requested or "")


if __name__ == "__main__":
    if MAX_TOKENS <= 0 or MIN_TOKENS <= 0 or MIN_TOKENS > MAX_TOKENS:
        raise SystemExit("Invalid token budget configuration")
    if RATE_LIMIT <= 0 or RATE_WINDOW <= 0 or MAX_ATTEMPTS <= 0 or MAX_CONCURRENCY <= 0:
        raise SystemExit("Invalid gateway admission configuration")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
