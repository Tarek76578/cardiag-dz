#!/usr/bin/env python3
"""CarDiag AI Gateway.

The recovery lifecycle is deliberately inspired by the provider-admission patterns
used by the MIT-licensed Free Claude Code project: classify upstream failures,
keep a bounded per-provider attempt budget, coordinate concurrency per provider,
and route to the next healthy provider before returning a terminal error.
"""

from __future__ import annotations

import json
import os
import random
import threading
import time
from collections import deque
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

MAX_TOKENS = int(os.getenv("AI_GATEWAY_MAX_OUTPUT_TOKENS", "12000"))
MIN_TOKENS = int(os.getenv("AI_GATEWAY_MIN_OUTPUT_TOKENS", "1024"))
TIMEOUT = int(os.getenv("AI_GATEWAY_TIMEOUT", "300"))
MAX_ATTEMPTS = int(os.getenv("AI_GATEWAY_MAX_ATTEMPTS", "3"))
BASE_COOLDOWN = float(os.getenv("AI_GATEWAY_COOLDOWN_SECONDS", "30"))
MAX_COOLDOWN = float(os.getenv("AI_GATEWAY_MAX_COOLDOWN_SECONDS", "300"))
JITTER = float(os.getenv("AI_GATEWAY_JITTER_SECONDS", "1"))
RATE_LIMIT = int(os.getenv("AI_GATEWAY_RATE_LIMIT", "20"))
RATE_WINDOW = float(os.getenv("AI_GATEWAY_RATE_WINDOW_SECONDS", "60"))

DEFAULT_PROVIDER_ORDER = "openrouter,groq,nvidia,deepseek,gemini"
ORDER = [x.strip() for x in os.getenv("AI_GATEWAY_PROVIDER_ORDER", DEFAULT_PROVIDER_ORDER).split(",") if x.strip()]

PROVIDERS: dict[str, dict[str, str]] = {
    "openrouter": {
        "base": os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"),
        "key": os.getenv("OPEN_ROUTER_API_KEY", ""),
        "model": os.getenv("OPENROUTER_MODEL", ""),
        "protocol": "responses",
    },
    "groq": {
        "base": os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1"),
        "key": os.getenv("GROQ_API_KEY", ""),
        "model": os.getenv("GROQ_MODEL", "llama-3.3-70b-versatile"),
        "protocol": "responses",
    },
    "nvidia": {
        "base": os.getenv("NVIDIA_BASE_URL", "https://integrate.api.nvidia.com/v1"),
        "key": os.getenv("NVIDIA_API_KEY", ""),
        "model": os.getenv("NVIDIA_MODEL", "meta/llama-3.1-8b-instruct"),
        "protocol": "chat",
    },
    "deepseek": {
        "base": os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
        "key": os.getenv("DEEPSEEK_API_KEY", ""),
        "model": os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash"),
        "protocol": "responses",
    },
    "gemini": {
        "base": os.getenv("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai"),
        "key": os.getenv("GEMINI_API_KEY", os.getenv("GEMINI_KEY", "")),
        "model": os.getenv("GEMINI_MODEL", "gemini-2.5-flash"),
        "protocol": "chat",
    },
}

state_lock = threading.Lock()
provider_locks = {name: threading.Lock() for name in PROVIDERS}


@dataclass
class ProviderState:
    failures: int = 0
    successes: int = 0
    cooldown_until: float = 0.0
    last_error: str = ""
    recovery_generation: int = 0
    recent_calls: deque[float] | None = None


provider_state = {
    name: ProviderState(recent_calls=deque()) for name in PROVIDERS
}


def clamp_budget(body: dict[str, Any]) -> None:
    field = "max_output_tokens" if "max_output_tokens" in body else "max_tokens"
    value = body.get(field)
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        body["max_output_tokens"] = MAX_TOKENS
        print(f"TOKEN_INJECT effective={MAX_TOKENS}", flush=True)
    elif value > MAX_TOKENS:
        body[field] = MAX_TOKENS
        print(f"TOKEN_CLAMP field={field} original={value} effective={MAX_TOKENS}", flush=True)


def body_text(data: bytes | str | Any) -> str:
    if isinstance(data, bytes):
        return data.decode("utf-8", "replace")
    if isinstance(data, str):
        return data
    return json.dumps(data, ensure_ascii=False, default=str)


def classify(code: int | None, data: bytes | str) -> str:
    low = body_text(data).lower()
    if code == 429 or any(x in low for x in ("rate_limit", "rate limit", "too many requests", "resource exhausted")):
        return "rate_limit"
    if code in (408, 425, 500, 502, 503, 504) or any(x in low for x in ("timeout", "overloaded", "capacity", "temporarily unavailable")):
        return "transient"
    if code == 401 or "invalid api key" in low or "authentication" in low:
        return "auth"
    if code == 402 or "payment required" in low or "billing" in low or "credits" in low and "required" in low:
        return "billing"
    if code == 403:
        return "permission"
    if code == 413 or "context length" in low or "too large" in low:
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
            pass
    low = body_text(data).lower()
    marker = "retry after"
    if marker in low:
        tail = low.split(marker, 1)[1].strip(" :.,\"')")
        token = ""
        for ch in tail:
            if ch.isdigit() or ch == ".":
                token += ch
            elif token:
                break
        if token:
            try:
                return max(0.0, float(token))
            except ValueError:
                pass
    return None


def model_for(provider: str, requested: str | None) -> str:
    cfg = PROVIDERS[provider]
    if provider == "openrouter" and requested and requested != "interceptor-test":
        return requested
    if cfg["model"]:
        return cfg["model"]
    return requested or ""


def available(provider: str) -> bool:
    cfg = PROVIDERS[provider]
    if not cfg["key"] or not model_for(provider, None):
        return False
    with state_lock:
        state = provider_state[provider]
        return time.monotonic() >= state.cooldown_until


def acquire_rate_slot(provider: str) -> None:
    while True:
        now = time.monotonic()
        with state_lock:
            calls = provider_state[provider].recent_calls
            assert calls is not None
            while calls and now - calls[0] >= RATE_WINDOW:
                calls.popleft()
            if len(calls) < RATE_LIMIT:
                calls.append(now)
                return
            delay = RATE_WINDOW - (now - calls[0])
        time.sleep(max(0.05, delay))


def mark_failure(provider: str, reason: str, delay: float | None = None) -> None:
    with state_lock:
        state = provider_state[provider]
        state.failures += 1
        state.last_error = reason
        state.recovery_generation += 1
        if delay is None:
            delay = min(MAX_COOLDOWN, BASE_COOLDOWN * (2 ** min(state.failures - 1, 4)))
        state.cooldown_until = time.monotonic() + max(0.0, delay)


def mark_success(provider: str) -> None:
    with state_lock:
        state = provider_state[provider]
        state.successes += 1
        state.failures = 0
        state.last_error = ""
        state.cooldown_until = 0.0


def provider_candidates(requested_provider: str, requested_model: str | None) -> list[tuple[str, str]]:
    preferred = requested_provider if requested_provider in PROVIDERS else "openrouter"
    ordered = [preferred] + [p for p in ORDER if p != preferred]
    result: list[tuple[str, str]] = []
    for provider in ordered:
        if available(provider):
            result.append((provider, model_for(provider, requested_model)))
    return result


def input_to_messages(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, str):
        return [{"role": "user", "content": value}]
    if isinstance(value, list):
        messages: list[dict[str, Any]] = []
        for item in value:
            if not isinstance(item, dict):
                continue
            if item.get("type") == "message":
                role = item.get("role", "user")
                content = item.get("content", "")
                if isinstance(content, list):
                    text_parts = [x.get("text", "") for x in content if isinstance(x, dict) and x.get("type") in ("input_text", "text")]
                    content = "".join(text_parts)
                messages.append({"role": role, "content": content})
            elif item.get("role") in ("system", "developer", "user", "assistant"):
                messages.append({"role": item["role"], "content": item.get("content", "")})
        if messages:
            return messages
    return [{"role": "user", "content": body_text(value)}]


def build_payload(provider: str, body: dict[str, Any], model: str) -> tuple[str, dict[str, Any]]:
    cfg = PROVIDERS[provider]
    payload = dict(body)
    payload["model"] = model
    if body.get("model") == "interceptor-test":
        payload["model"] = model
    if cfg["protocol"] == "responses":
        return cfg["base"].rstrip("/") + "/responses", payload

    # NVIDIA NIM and Gemini currently expose OpenAI-compatible chat completions.
    chat: dict[str, Any] = {
        "model": model,
        "messages": input_to_messages(body.get("input", "")),
        "stream": bool(body.get("stream", False)),
    }
    if "instructions" in body and body["instructions"]:
        chat["messages"] = [{"role": "system", "content": body["instructions"]}] + chat["messages"]
    if "max_output_tokens" in body:
        chat["max_tokens"] = body["max_output_tokens"]
    elif "max_tokens" in body:
        chat["max_tokens"] = body["max_tokens"]
    for key in ("temperature", "top_p", "tools", "tool_choice"):
        if key in body:
            chat[key] = body[key]
    return cfg["base"].rstrip("/") + "/chat/completions", chat


def upstream_call(provider: str, body: dict[str, Any], accept: str | None):
    model = model_for(provider, body.get("model"))
    url, payload = build_payload(provider, body, model)
    key = PROVIDERS[provider]["key"]
    headers = {
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
        "Accept": accept or "application/json",
        "HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz",
        "X-Title": "CarDiag Autonomous Agent",
    }
    return urlopen(Request(url, data=json.dumps(payload, separators=(",", ":")).encode(), method="POST", headers=headers), timeout=TIMEOUT)


def forward_response(handler: BaseHTTPRequestHandler, response: Any) -> None:
    with response as upstream_response:
        content_type = upstream_response.headers.get("Content-Type", "application/json")
        handler.send_response(upstream_response.status)
        handler.send_header("Content-Type", content_type)
        handler.send_header("Cache-Control", "no-cache")
        handler.send_header("Connection", "close")
        handler.send_header("Transfer-Encoding", "chunked")
        handler.end_headers()
        committed = False
        while True:
            chunk = upstream_response.read(16384)
            if not chunk:
                break
            committed = True
            handler.wfile.write(f"{len(chunk):X}\r\n".encode())
            handler.wfile.write(chunk)
            handler.wfile.write(b"\r\n")
            handler.wfile.flush()
        handler.wfile.write(b"0\r\n\r\n")
        handler.wfile.flush()
        return committed


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
                        "model": cfg["model"],
                        "failures": state.failures,
                        "successes": state.successes,
                        "cooldown_remaining": max(0.0, state.cooldown_until - time.monotonic()),
                        "last_error": state.last_error,
                        "recovery_generation": state.recovery_generation,
                    }
                    for name, cfg in PROVIDERS.items()
                    for state in [provider_state[name]]
                }
            self.send_json(200, {"status": "ok", "providers": providers})
            return
        if path == "/v1/models":
            data = [
                {"id": model_for(p, None), "object": "model", "owned_by": p}
                for p in ORDER if p in PROVIDERS and available(p)
            ]
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
        tried: list[str] = []

        for provider, model in provider_candidates(requested_provider, body.get("model")):
            tried.append(f"{provider}/{model}")
            with provider_locks[provider]:
                # Another request may have cooled this provider while we waited.
                if not available(provider):
                    continue
                for attempt in range(1, MAX_ATTEMPTS + 1):
                    acquire_rate_slot(provider)
                    print(f"ROUTE provider={provider} model={model} attempt={attempt}/{MAX_ATTEMPTS}", flush=True)
                    try:
                        response = upstream_call(provider, body, self.headers.get("Accept"))
                        forward_response(self, response)
                        mark_success(provider)
                        return
                    except HTTPError as exc:
                        data = exc.read()
                        kind = classify(exc.code, data)
                        print(f"UPSTREAM_ERROR provider={provider} code={exc.code} class={kind}", flush=True)
                        if kind in ("auth", "permission", "billing", "invalid_request", "request_too_large"):
                            mark_failure(provider, kind, MAX_COOLDOWN)
                            break
                        delay = retry_after(exc.headers, data)
                        if kind in ("rate_limit", "transient") and attempt < MAX_ATTEMPTS:
                            if delay is None:
                                delay = min(MAX_COOLDOWN, BASE_COOLDOWN * attempt)
                            delay += random.uniform(0.0, JITTER)
                            mark_failure(provider, kind, delay)
                            print(f"RECOVERY provider={provider} generation={provider_state[provider].recovery_generation} sleep={delay:.2f}", flush=True)
                            time.sleep(delay)
                            continue
                        mark_failure(provider, kind)
                        break
                    except (URLError, TimeoutError) as exc:
                        mark_failure(provider, "network")
                        print(f"UPSTREAM_NETWORK_ERROR provider={provider} error={type(exc).__name__}", flush=True)
                        break
                    except (BrokenPipeError, ConnectionResetError):
                        return
                    except Exception as exc:
                        mark_failure(provider, "unexpected")
                        print(f"UPSTREAM_EXCEPTION provider={provider} error={type(exc).__name__}", flush=True)
                        break

        self.send_json(503, {
            "error": {
                "message": "All configured AI providers failed",
                "type": "provider_exhausted",
                "providers_tried": tried,
            }
        })


if __name__ == "__main__":
    if MAX_TOKENS <= 0 or MIN_TOKENS <= 0 or MIN_TOKENS > MAX_TOKENS:
        raise SystemExit("Invalid token budget configuration")
    if RATE_LIMIT <= 0 or RATE_WINDOW <= 0 or MAX_ATTEMPTS <= 0:
        raise SystemExit("Invalid gateway admission configuration")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
