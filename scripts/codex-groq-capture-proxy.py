#!/usr/bin/env python3
"""Local Codex -> Groq Responses compatibility adapter.

Codex speaks the OpenAI Responses wire format, but it can attach Codex-specific
metadata/tools that Groq's Responses API does not accept. This adapter removes
only fields that Groq documents as unsupported and drops Codex-only namespace
agent tooling while preserving normal function tools and the rest of the request.
"""
import http.client
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOST = "127.0.0.1"
PORT = int(os.environ.get("CAPTURE_PROXY_PORT", "8787"))
UPSTREAM_HOST = "api.groq.com"
UPSTREAM_BASE = "/openai/v1"
CAPTURE = Path(os.environ.get("CAPTURE_FILE", "/tmp/codex-groq-request.json"))
ADAPTED_CAPTURE = Path(os.environ.get("ADAPTED_CAPTURE_FILE", "/tmp/codex-groq-adapted-request.json"))
RESPONSE_CAPTURE = Path(os.environ.get("RESPONSE_CAPTURE_FILE", "/tmp/codex-groq-response.json"))
MAX_CAPTURE = 2_000_000

# Groq documents these Responses fields as unsupported.
UNSUPPORTED_REQUEST_FIELDS = {
    "previous_response_id",
    "store",
    "truncation",
    "include",
    "safety_identifier",
    "prompt_cache_key",
    "prompt",
}
# Codex-only metadata is not part of Groq's Responses request schema.
CODEX_ONLY_FIELDS = {"client_metadata", "access_programs"}


def sanitize(value):
    if isinstance(value, dict):
        out = {}
        for k, v in value.items():
            if k.lower() in {"authorization", "api_key", "apikey", "key", "token"}:
                out[k] = "[REDACTED]"
            else:
                out[k] = sanitize(v)
        return out
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

    # Codex 0.151 can emit reasoning.summary. Groq's Responses endpoint rejects
    # that field even though the request-level reasoning object itself is valid.
    # Preserve supported reasoning controls (for example effort) and remove only
    # the incompatible summary member.
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

    # Codex may expose its internal multi-agent namespace tool. Groq's Responses
    # API accepts function/built-in/MCP tools, not Codex's namespace wrapper.
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

    # Do not send an empty tools array or a tool_choice that refers to a removed
    # namespace. With no remaining tools, let Groq apply its normal default.
    if adapted.get("tools") == []:
        adapted.pop("tools", None)
        if adapted.get("tool_choice") not in (None, "none", "auto"):
            removed.append("tool_choice")
            adapted.pop("tool_choice", None)

    return adapted, removed


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
            print(f"CAPTURED_REQUEST path={self.path} bytes={len(body)} file={CAPTURE}", flush=True)
            if isinstance(parsed, dict):
                print("CAPTURED_TOP_LEVEL_KEYS=" + ",".join(sorted(parsed.keys())), flush=True)
            adapted, removed = adapt_request(parsed)
            adapted_body = json.dumps(adapted, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            ADAPTED_CAPTURE.write_text(json.dumps(sanitize(adapted), indent=2, ensure_ascii=False)[:MAX_CAPTURE], encoding="utf-8")
            print("ADAPTER_REMOVED_FIELDS=" + ",".join(sorted(removed)) if removed else "ADAPTER_REMOVED_FIELDS=<none>", flush=True)
            print(f"ADAPTED_REQUEST bytes={len(adapted_body)} file={ADAPTED_CAPTURE}", flush=True)
        except Exception as exc:
            print(f"ADAPTER_REQUEST_ERROR={type(exc).__name__}: {exc}", flush=True)
            self.send_error(400, "invalid Codex request JSON")
            return

        headers = {}
        for key in ("Authorization", "Content-Type", "Accept", "OpenAI-Beta", "X-Client-Request-Id"):
            if self.headers.get(key):
                headers[key] = self.headers[key]
        headers["Host"] = UPSTREAM_HOST
        headers["Content-Length"] = str(len(adapted_body))
        headers["Connection"] = "close"

        conn = http.client.HTTPSConnection(UPSTREAM_HOST, 443, timeout=180)
        try:
            conn.request("POST", self.path, body=adapted_body, headers=headers)
            response = conn.getresponse()
            response_body = response.read()
            safe_response = response_body[:MAX_CAPTURE]
            try:
                RESPONSE_CAPTURE.write_text(safe_response.decode("utf-8", "replace"), encoding="utf-8")
            except Exception:
                RESPONSE_CAPTURE.write_bytes(safe_response)
            self.send_response(response.status, response.reason)
            hop_by_hop = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"}
            for key, value in response.getheaders():
                if key.lower() not in hop_by_hop:
                    self.send_header(key, value)
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(response_body)
            self.wfile.flush()
            print(f"UPSTREAM_RESPONSE status={response.status} content_type={response.getheader('Content-Type','')} bytes={len(response_body)}", flush=True)
            if response.status >= 400:
                print("UPSTREAM_ERROR_BODY=" + response_body[:1200].decode("utf-8", "replace"), flush=True)
        except Exception as exc:
            print(f"UPSTREAM_FORWARD_ERROR={type(exc).__name__}: {exc}", flush=True)
            try:
                self.send_error(502, "capture proxy upstream error")
            except Exception:
                pass
        finally:
            conn.close()


class Server(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    server = Server((HOST, PORT), Handler)
    print(f"CAPTURE_PROXY_LISTENING=http://{HOST}:{PORT}", flush=True)
    server.serve_forever()
