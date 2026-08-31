#!/usr/bin/env python3
import importlib.util
import pathlib
import sys
from email.message import Message

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("ai_gateway", ROOT / "scripts" / "ai-gateway.py")
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


def test_classification():
    assert module.classify(429, b'{"error":"rate_limit"}') == "rate_limit"
    assert module.classify(503, b"overloaded") == "transient"
    assert module.classify(401, b"invalid api key") == "auth"
    assert module.classify(402, b"credits required") == "billing"
    assert module.classify(413, b"context too large") == "request_too_large"
    assert module.classify(404, b"model not found") == "not_found"
    assert module.classify(400, b"bad request") == "invalid_request"


def test_retry_after():
    headers = Message(); headers["Retry-After"] = "17"
    assert module.retry_after(headers, b"") == 17.0
    assert module.retry_after(Message(), b"retry after 2.5 seconds") == 2.5


def test_budget_clamp():
    body = {"max_output_tokens": 65536}
    module.clamp_budget(body)
    assert body["max_output_tokens"] == module.MAX_TOKENS


def test_chat_payload():
    body = {"model": "x", "input": "hello", "max_output_tokens": 100}
    url, payload = module.build_payload("nvidia", body, "meta/llama-3.1-8b-instruct")
    assert url.endswith("/chat/completions")
    assert payload["messages"][0]["role"] == "user"
    assert payload["max_tokens"] == 100


def test_chat_response_normalization():
    raw = b'{"id":"chat_1","choices":[{"message":{"role":"assistant","content":"OK"}}]}'
    normalized = module.normalize_chat_response(raw, "gemini-3.7-flash")
    data = __import__("json").loads(normalized)
    assert data["object"] == "response"
    assert data["model"] == "gemini-3.7-flash"
    assert data["output"][0]["content"][0]["text"] == "OK"


def test_provider_capabilities():
    assert module.PROVIDERS["openrouter"]["codex"] is True
    assert module.PROVIDERS["groq"]["protocol"] == "responses"
    assert module.PROVIDERS["gemini"]["protocol"] == "chat"
    assert module.PROVIDERS["gemini"]["codex"] is False


def test_candidates_skip_unconfigured_and_cooling():
    original_key = module.PROVIDERS["groq"]["key"]
    original_model = module.PROVIDERS["groq"]["model"]
    original_cooldown = module.provider_state["groq"].cooldown_until
    try:
        module.PROVIDERS["groq"]["key"] = "test"
        module.PROVIDERS["groq"]["model"] = "test-model"
        module.provider_state["groq"].cooldown_until = 10**20
        assert all(provider != "groq" for provider, _ in module.provider_candidates("openrouter", "x"))
    finally:
        module.provider_state["groq"].cooldown_until = original_cooldown
        module.PROVIDERS["groq"]["key"] = original_key
        module.PROVIDERS["groq"]["model"] = original_model


def test_model_options_include_provider_fallbacks():
    original_model = module.PROVIDERS["groq"]["model"]
    try:
        module.PROVIDERS["groq"]["model"] = "openai/gpt-oss-120b"
        options = module.model_options("groq", "missing-model")
        assert options[0] == "openai/gpt-oss-120b"
        assert "openai/gpt-oss-20b" in options
    finally:
        module.PROVIDERS["groq"]["model"] = original_model


def test_failure_policy_exports():
    assert module.classify(429, b"") == "rate_limit"
    assert module.classify(503, b"") == "transient"
    assert module.classify(403, b"") == "permission"
    assert module.classify(404, b"") == "not_found"
    assert module.classify(400, b"") == "invalid_request"


if __name__ == "__main__":
    for test in (
        test_classification,
        test_retry_after,
        test_budget_clamp,
        test_chat_payload,
        test_chat_response_normalization,
        test_provider_capabilities,
        test_candidates_skip_unconfigured_and_cooling,
        test_model_options_include_provider_fallbacks,
        test_failure_policy_exports,
    ):
        test()
    print("AI_GATEWAY_TESTS=PASS")
