#!/usr/bin/env python3
"""CarDiag AI Gateway.

Provider recovery follows the same useful boundaries studied in the MIT-licensed
Free Claude Code project: canonical failure classes, bounded attempts, per-provider
admission, cooldown/recovery state, and controlled fallback. This file is an
independent implementation for CarDiag; it does not copy FCC source code.
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

DEFAULT_PROVIDER_ORDER = "openrouter,groq,nvidia,deepseek,gemini"
ORDER = [x.strip() for x in os.getenv("AI_GATEWAY_PROVIDER_ORDER", DEFAULT_PROVIDER_ORDER).split(",") if x.strip()]

PROVIDERS: dict[str, dict[str, Any]] = {
    "openrouter": {
        "base": os.getenv("OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"),
        "key": os.getenv("OPEN_ROUTER_API_KEY", ""),
        "model": os.getenv("OPENROUTER_MODEL", ""),
        "models": os.getenv("OPENROUTER_FALLBACK_MODELS", "openai/gpt-oss-120b:free,openai/gpt-oss-20b:free,qwen/qwen3-coder:free,deepseek/deepseek-chat-v3.1:free"),
        "protocol": "responses",
        "codex": True,
    },
    "groq": {
        "base": os.getenv("GROQ_BASE_URL", "https://api.groq.com/openai/v1"),
        "key": os.getenv("GROQ_API_KEY", ""),
        "model": os.getenv("GROQ_MODEL", "openai/gpt-oss-120b"),
        "models": os.getenv("GROQ_FALLBACK_MODELS", "openai/gpt-oss-120b,openai/gpt-oss-20b,llama-3.3-70b-versatile"),
        "protocol": "responses",
        "codex": True,
    },
    "nvidia": {
        "base": os.getenv("NVIDIA_BASE_URL", "https://integrate.api.nvidia.com/v1"),
        "key": os.getenv("NVIDIA_API_KEY", ""),
        "model": os.getenv("NVIDIA_MODEL", "meta/llama-3.1-8b-instruct"),
        "models": os.getenv("NVIDIA_FALLBACK_MODELS", "meta/llama-3.1-8b-instruct"),
        "protocol": "chat",
        "codex": False,
    },
    "deepseek": {
        "base": os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
        "key": os.getenv("DEEPSEEK_API_KEY", ""),
        "model": os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
        "models": os.getenv("DEEPSEEK_FALLBACK_MODELS", "deepseek-chat,deepseek-reasoner"),
        "protocol": "responses",
        "codex": True,
    },
    "gemini": {
        "base": os.getenv("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta/openai"),
        "key": os.getenv("GEMINI_API_KEY", os.getenv("GEMINI_KEY", "")),
        "model": os.getenv("GEMINI_MODEL", "gemini-3.7-flash"),
        "models": os.getenv("GEMINI_FALLBACK_MODELS", "gemini-3.7-flash,gemini-2.5-flash"),
        "protocol": "chat",
        "codex": False,
    },
}

state_lock = threading.RLock()


@dataclass
class ProviderState:
    failures: int = 0
    successes: int = 0
    cooldown_until: float = 0.0
    last_error: str = ""
    recovery_generation: int = 0
    recent_calls: deque[float] = field(default_factory=deque)
    discovered_models: list[str] = field(default_factory=list)


provider_state = {name: ProviderState() for name in PROVIDERS}
provider_locks = {name: threading.Lock() for name in PROVIDERS}
provider_conditions = {name: threading.Condition(state_lock) for name in PROVIDERS}


def body_text(data: Any) -> str:
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
    if code == 401 or "invalid api key" in low or "authentication failed" in low:
        return "auth"
    if code == 402 or "payment required" in low or "billing" in low:
        return "billing"
    if code == 403 or "permission denied" in low or "forbidden" in low:
        return "permission"
    if code == 404 or "model not found" in low or "not found" in low:
        return "not_found"
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
            try:
                return max(0.0, (parsedate_to_datetime(raw).timestamp() - time.time()))
            except Exception:
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


def clamp_budget(body: dict[str, Any]) -> None:
    field = "max_output_tokens" if "max_output_tokens" in body else "max_tokens"
    value = body.get(field)
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        body["max_output_tokens"] = MAX_TOKENS
        print(f"TOKEN_INJECT field=max_output_tokens effective={MAX_TOKENS}", flush=True)
    elif value > MAX_TOKENS:
        body[field] = MAX_TOKENS
        print(f"TOKEN_CLAMP field={field} original={value} effective={MAX_TOKENS}", flush=True)


def available(provider: str, *, codex_request: bool = True) -> bool:
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


def mark_failure(provider: str, reason: str, delay: float | None = None) -> None:
    with state_lock:
        state = provider_state[provider]
        state.failures += 1
        state.last_error = reason
        state.recovery_generation += 1
        if delay is None:
            delay = min(MAX_COOLDOWN, BASE_COOLDOWN * (2 ** min(state.failures - 1, 4)))
        state.cooldown_until = time.monotonic() + max(0.0, delay)
        provider_conditions[provider].notify_all()


def mark_success(provider: str) -> None:
    with state_lock:
        state = provider_state[provider]
        state.successes += 1
        state.failures = 0
        state.last_error = ""
        state.cooldown_until = 0.0
        provider_conditions[provider].notify_all()


def provider_candidates(requested_provider: str, requested_model: str | None, *, codex_request: bool = True) -> list[tuple[str, str]]:
    preferred = requested_provider if requested_provider in PROVIDERS else "openrouter"
    ordered = [preferred] + [p for p in ORDER if p != preferred]
    result: list[tuple[str, str]] = []
    for provider in ordered:
        if not available(provider, codex_request=codex_request):
            continue
        result.append((provider, model_for(provider, requested_model)))
    return result


def model_for(provider: str, requested: str | None) -> str:
    cfg = PROVIDERS[provider]
    configured = cfg["model"]
    candidates = [x.strip() for x in str(cfg.get("models", "")).split(",") if x.strip()]
    discovered = provider_state[provider].discovered_models
    if requested and requested not in ("interceptor-test", ""):
        if provider == "openrouter" and requested in candidates:
            return requested
        if provider != "openrouter" and requested in candidates:
            return requested
    if configured:
        return configured
    if candidates:
        return candidates[0]
    if discovered:
        return discovered[0]
    return requested or ""


def discover_models(provider: str) -> list[str]:
    cfg = PROVIDERS[provider]
    if not cfg["key"]:
        return []
    url = cfg["base"].rstrip("/") + "/models"
    req = Request(url, headers={"Authorization": f"Bearer {cfg['key']}", "Accept": "application/json"}, method="GET")
    try:
        with urlopen(req, timeout=min(20, TIMEOUT)) as response:
            data = json.loads(response.read().decode("utf-8", "replace"))
    except Exception as exc:
        print(f"MODEL_DISCOVERY_ERROR provider={provider} error={type(exc).__name__}", flush=True)
        return []
    models = [str(x.get("id")) for x in data.get("data", []) if isinstance(x, dict) and x.get("id")]
    if provider == "openrouter":
        scored: list[tuple[int, str]] = []
        for model in models:
            low = model.lower()
            score = 0
            if low.endswith(":free"):
                score += 10000
            if "coder" in low or "code" in low:
                score += 2500
            if "gpt-oss" in low:
                score += 2200
            if "deepseek" in low or "qwen" in low:
                score += 1000
            scored.append((score, model))
        models = [m for _, m in sorted(scored, reverse=True)]
    else:
        preference = [x.strip() for x in str(cfg.get("models", "")).split(",") if x.strip()]
        models = sorted(models, key=lambda m: (preference.index(m) if m in preference else len(preference), m))
    with state_lock:
        provider_state[provider].discovered_models = models[:50]
    print(f"MODEL_DISCOVERY provider={provider} count={len(models)}", flush=True)
    return models


def model_options(provider: str, requested: str | None) -> list[str]:
    cfg = PROVIDERS[provider]
    options: list[str] = []
    preferred = model_for(provider, requested)
    if preferred:
        options.append(preferred)
    for value in [x.strip() for x in str(cfg.get("models", "")).split(",") if x.strip()]:
        if value not in options:
            options.append(value)
    with state_lock:
        for value in provider_state[provider].discovered_models:
            if value not in options:
                options.append(value)
    if len(options) <= 1:
        discovered = discover_models(provider)
        for value in discovered:
            if value not in options:
                options.append(value)
    return options


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
                    parts = []
                    for part in content:
                        if isinstance(part, dict) and part.get("type") in ("input_text", "text"):
                            parts.append(str(part.get("text", "")))
                    content = "".join(parts)
                messages.append({"role": role, "content": content})
            elif item.get("role") in ("system", "developer", "user", "assistant"):
                messages.append({"role": item["role"], "content": item.get("content", "")})
        if messages:
            return messages
    return [{"role": "user", "content": body_text(value)}]


def build_payload(provider: str, body: dict[str, Any], model: str) -> tuple[str, dict[str, Any]]:
    cfg = PROVIDERS[provider]
    if cfg["protocol"] == "responses":
        payload = dict(body)
        payload["model"] = model
        return cfg["base"].rstrip("/") + "/responses", payload
    chat: dict[str, Any] = {
        "model": model,
        "messages": input_to_messages(body.get("input", "")),
        "stream": bool(body.get("stream", False)),
    }
    instructions = body.get("instructions")
    if instructions:
        chat["messages"] = [{"role": "system", "content": instructions}] + chat["messages"]
    if "max_output_tokens" in body:
        chat["max_tokens"] = body["max_output_tokens"]
    elif "max_tokens" in body:
        chat["max_tokens"] = body["max_tokens"]
    for key in ("temperature", "top_p", "tools", "tool_choice", "parallel_tool_calls"):
        if key in body:
            chat[key] = body[key]
    return cfg["base"].rstrip("/") + "/chat/completions", chat


def upstream_call(provider: str, body: dict[str, Any], model: str, accept: str | None):
    url, payload = build_payload(provider, body, model)
    headers = {
        "Authorization": f"Bearer {PROVIDERS[provider]['key']}",
        "Content-Type": "application/json",
        "Accept": accept or "application/json",
    }
    if provider == "openrouter":
        headers.update({"HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz", "X-Title": "CarDiag Autonomous Agent"})
    request = Request(url, data=json.dumps(payload, separators=(",", ":")).encode(), method="POST", headers=headers)
    return urlopen(request, timeout=TIMEOUT)


def forward_responses(handler: BaseHTTPRequestHandler, response: Any) -> bool:
    committed = False
    with response as upstream:
        content_type = upstream.headers.get("Content-Type", "application/json")
        handler.send_response(upstream.status)
        handler.send_header("Content-Type", content_type)
        handler.send_header("Cache-Control", "no-cache")
        handler.send_header("Connection", "close")
        handler.send_header("Transfer-Encoding", "chunked")
        handler.end_headers()
        while True:
            chunk = upstream.read(16384)
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


def normalize_chat_response(response_bytes: bytes, model: str) -> bytes:
    data = json.loads(response_bytes.decode("utf-8", "replace"))
    choice = (data.get("choices") or [{}])[0]
    message = choice.get("message") or {}
    content = message.get("content") or ""
    output = [{
        "type": "message",
        "id": "msg_cardiag",
        "status": "completed",
        "role": "assistant",
        "content": [{"type": "output_text", "text": content}],
    }]
    result = {
        "id": data.get("id", "resp_cardiag"),
        "object": "response",
        "status": "completed",
        "model": model,
        "output": output,
    }
    return json.dumps(result, separators=(",", ":")).encode()


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
                providers = {}
                for name, cfg in PROVIDERS.items():
                    state = provider_state[name]
                    providers[name] = {
                        "configured": bool(cfg["key"]),
                        "codex_compatible": bool(cfg["codex"]),
                        "protocol": cfg["protocol"],
                        "model": cfg["model"],
                        "failures": state.failures,
                        "successes": state.successes,
                        "cooldown_remaining": max(0.0, state.cooldown_until - time.monotonic()),
                        "last_error": state.last_error,
                        "recovery_generation": state.recovery_generation,
                        "discovered_models": len(state.discovered_models),
                    }
            self.send_json(200, {"status": "ok", "providers": providers})
            return
        if path == "/v1/models":
            data = []
            for name in ORDER:
                if available(name):
                    data.append({"id": model_for(name, None), "object": "model", "owned_by": name})
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
        codex_request = requested_provider == "openrouter" or os.getenv("CODEX_PROVIDER") == "cardiag_gateway"
        tried: list[str] = []

        for provider, initial_model in provider_candidates(requested_provider, requested_model, codex_request=codex_request):
            if provider in tried:
                continue
            tried.append(f"{provider}/{initial_model}")
            with provider_locks[provider]:
                if not available(provider, codex_request=codex_request):
                    continue
                last_kind = "upstream"
                options = [initial_model] + [m for m in model_options(provider, requested_model) if m != initial_model]
                attempt = 0
                option_index = 0
                while attempt < MAX_ATTEMPTS and option_index < len(options):
                    model = options[option_index]
                    attempt += 1
                    acquire_rate_slot(provider)
                    print(f"ROUTE provider={provider} model={model} attempt={attempt}/{MAX_ATTEMPTS}", flush=True)
                    try:
                        response = upstream_call(provider, body, model, self.headers.get("Accept"))
                        if PROVIDERS[provider]["protocol"] == "chat":
                            raw = response.read()
                            response.close()
                            normalized = normalize_chat_response(raw, model)
                            self.send_response(200)
                            self.send_header("Content-Type", "application/json")
                            self.send_header("Content-Length", str(len(normalized)))
                            self.send_header("Connection", "close")
                            self.end_headers()
                            self.wfile.write(normalized)
                            self.wfile.flush()
                        else:
                            committed = forward_responses(self, response)
                            if not committed:
                                print(f"STREAM_EMPTY provider={provider}", flush=True)
                        mark_success(provider)
                        return
                    except HTTPError as exc:
                        data = exc.read()
                        last_kind = classify(exc.code, data)
                        print(f"UPSTREAM_ERROR provider={provider} model={model} code={exc.code} class={last_kind}", flush=True)
                        if last_kind in ("not_found", "permission"):
                            # A provider-specific model failure is a routing failure, not a global outage.
                            option_index += 1
                            continue
                        if last_kind in ("auth", "billing", "invalid_request", "request_too_large"):
                            mark_failure(provider, last_kind, MAX_COOLDOWN)
                            break
                        delay = retry_after(exc.headers, data)
                        if last_kind in ("rate_limit", "transient") and attempt < MAX_ATTEMPTS:
                            delay = delay if delay is not None else min(MAX_COOLDOWN, BASE_COOLDOWN * attempt)
                            delay += random.uniform(0.0, JITTER)
                            mark_failure(provider, last_kind, delay)
                            print(f"RECOVERY provider={provider} generation={provider_state[provider].recovery_generation} sleep={delay:.2f}", flush=True)
                            time.sleep(delay)
                            continue
                        mark_failure(provider, last_kind)
                        break
                    except (URLError, TimeoutError) as exc:
                        last_kind = "network"
                        mark_failure(provider, last_kind)
                        print(f"UPSTREAM_NETWORK_ERROR provider={provider} model={model} error={type(exc).__name__}", flush=True)
                        break
                    except (BrokenPipeError, ConnectionResetError):
                        return
                    except Exception as exc:
                        last_kind = "unexpected"
                        mark_failure(provider, last_kind)
                        print(f"UPSTREAM_EXCEPTION provider={provider} model={model} error={type(exc).__name__}", flush=True)
                        break
                print(f"PROVIDER_EXHAUSTED provider={provider} last_class={last_kind}", flush=True)

        self.send_json(503, {"error": {"message": "All compatible AI providers failed", "type": "provider_exhausted", "providers_tried": tried}})


if __name__ == "__main__":
    if MAX_TOKENS <= 0 or MIN_TOKENS <= 0 or MIN_TOKENS > MAX_TOKENS:
        raise SystemExit("Invalid token budget configuration")
    if RATE_LIMIT <= 0 or RATE_WINDOW <= 0 or MAX_ATTEMPTS <= 0 or MAX_CONCURRENCY <= 0:
        raise SystemExit("Invalid gateway admission configuration")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
