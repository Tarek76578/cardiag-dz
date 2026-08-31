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
    headers = Message()
    headers["Retry-After"] = "17"
    assert module.retry_after(headers, b"") == 17.0
    assert module.retry_after(Message(), b"retry after 2.5 seconds") == 2.5


def test_budget_clamp():
    body = {"max_output_tokens": 65536}
    module.clamp_budget(body)
    assert body["max_output_tokens"] == module.MAX_TOKENS


def test_provider_protocols():
    assert module.PROVIDERS["openrouter"]["codex"] is True
    assert module.PROVIDERS["groq"]["codex"] is True
    assert module.PROVIDERS["deepseek"]["codex"] is True
    assert all(module.PROVIDERS[name]["codex"] for name in module.PROVIDERS)


def test_provider_model_isolation():
    original_or = module.PROVIDERS["openrouter"]["model"]
    original_groq = module.PROVIDERS["groq"]["model"]
    original_ds = module.PROVIDERS["deepseek"]["model"]
    try:
        module.PROVIDERS["openrouter"]["model"] = "openai/gpt-oss-120b:free"
        module.PROVIDERS["groq"]["model"] = "openai/gpt-oss-120b"
        module.PROVIDERS["deepseek"]["model"] = "deepseek-chat"
        foreign = "openai/gpt-oss-120b:free"
        assert module.model_for("openrouter", foreign) == foreign
        assert module.model_for("groq", foreign) == "openai/gpt-oss-120b"
        assert module.model_for("deepseek", foreign) == "deepseek-chat"
    finally:
        module.PROVIDERS["openrouter"]["model"] = original_or
        module.PROVIDERS["groq"]["model"] = original_groq
        module.PROVIDERS["deepseek"]["model"] = original_ds


def test_retry_recovery_state():
    state = module.provider_state["groq"]
    old = (state.failures, state.cooldown_until, state.recovery_generation)
    try:
        module.mark_failure("groq", "rate_limit", 1.0)
        assert state.failures == old[0] + 1
        assert state.cooldown_until > 0
        assert state.recovery_generation == old[2] + 1
        assert not module.provider_ready("groq", True)
    finally:
        state.failures, state.cooldown_until, state.recovery_generation = old


def test_stream_commit_marker():
    assert issubclass(module.StreamCommittedError, RuntimeError)


def test_candidates_respect_cooldown_and_codex():
    originals = {}
    try:
        for name in module.PROVIDERS:
            originals[name] = (module.PROVIDERS[name]["key"], module.PROVIDERS[name]["model"])
            module.PROVIDERS[name]["key"] = "test-key"
            module.PROVIDERS[name]["model"] = name + "-model"
        module.provider_state["groq"].cooldown_until = 10**20
        candidates = module.candidate_providers("openrouter", True)
        assert "groq" not in candidates
        assert "openrouter" in candidates
    finally:
        for name, (key, model) in originals.items():
            module.PROVIDERS[name]["key"] = key
            module.PROVIDERS[name]["model"] = model
        module.provider_state["groq"].cooldown_until = 0.0


if __name__ == "__main__":
    for test in (
        test_classification,
        test_retry_after,
        test_budget_clamp,
        test_provider_protocols,
        test_provider_model_isolation,
        test_retry_recovery_state,
        test_stream_commit_marker,
        test_candidates_respect_cooldown_and_codex,
    ):
        test()
    print("AI_GATEWAY_TESTS=PASS")
