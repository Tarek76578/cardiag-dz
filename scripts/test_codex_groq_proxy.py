#!/usr/bin/env python3
import importlib.util
from pathlib import Path

p = Path(__file__).with_name("codex-groq-capture-proxy.py")
spec = importlib.util.spec_from_file_location("groq_proxy", p)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

CEILING = 7000


def assert_budget(obj):
    out = obj["max_output_tokens"]
    est = mod.token_estimate(obj.get("input", ""))
    total = est + out + mod.REQUEST_OVERHEAD_TOKENS
    assert total <= CEILING, (est, out, total)
    return est, total


def run():
    huge = [{"role": "system", "content": "system " * 5000}]
    huge += [{"role": "user", "content": "old " * 3000} for _ in range(8)]
    huge += [{"role": "assistant", "content": "new " * 3000}, {"role": "user", "content": "finish this task"}]
    obj = {"model": "openai/gpt-oss-120b", "input": huge, "max_output_tokens": 800}
    before = mod.token_estimate(obj["input"])
    compacted, changed, est, total = mod.enforce_budget(obj)
    assert changed
    assert est < before
    assert_budget(compacted)
    assert isinstance(compacted["input"], list)
    assert compacted["input"][-1]["role"] == "user"

    text = {"model": "openai/gpt-oss-120b", "input": "x" * 100000, "max_output_tokens": 800}
    compacted, changed, est, total = mod.enforce_budget(text)
    assert changed
    assert_budget(compacted)

    tools = []
    for i in range(40):
        tools.append({"type": "function_call", "call_id": f"c{i}", "arguments": "x" * 2000})
        tools.append({"type": "function_call_output", "call_id": f"c{i}", "output": "y" * 2000})
    obj = {"model": "openai/gpt-oss-120b", "input": tools, "max_output_tokens": 800}
    compacted, changed, est, total = mod.enforce_budget(obj)
    assert changed
    assert_budget(compacted)
    for item in compacted["input"]:
        assert item.get("type") in {"function_call", "function_call_output"}
    print("GROQ_PROXY_REGRESSION_TEST=PASS")


if __name__ == "__main__":
    run()
