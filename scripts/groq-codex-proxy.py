#!/usr/bin/env python3
"""Compatibility bridge: Codex Responses API -> Groq Responses API.

Groq's Responses API is OpenAI-compatible, but its documented unsupported
fields are stricter than the payload Codex can emit for custom providers.
This localhost-only bridge removes unsupported optional fields and keeps only
standard function tools before forwarding the request to Groq.
"""
import http.client
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit

UPSTREAM = os.environ.get("GROQ_UPSTREAM", "https://api.groq.com/openai/v1")
API_KEY = os.environ["GROQ_API_KEY"]
PORT = int(os.environ.get("GROQ_PROXY_PORT", "8787"))

UNSUPPORTED_TOP_LEVEL = {
    "previous_response_id",
    "store",
    "truncation",
    "include",
    "safety_identifier",
    "prompt_cache_key",
    "prompt",
    "client_metadata",
}


def sanitize_tool(tool):
    if not isinstance(tool, dict):
        return None
    kind = tool.get("type")
    if kind == "function":
        return tool
    if kind == "namespace":
        # Flatten namespace containers into ordinary function tools. Groq's
        # public Responses API documents function tools; Codex can execute
        # these client-side after receiving the function call.
        nested = tool.get("tools") or []
        return [x for x in (sanitize_tool(t) for t in nested) if isinstance(x, dict)]
    return None


def sanitize_body(raw):
    body = json.loads(raw.decode("utf-8"))
    if not isinstance(body, dict):
        raise ValueError("request body must be a JSON object")
    for key in UNSUPPORTED_TOP_LEVEL:
        body.pop(key, None)

    # Groq supports reasoning, input, tools, tool_choice and parallel_tool_calls.
    # Keep the documented Responses fields and remove Codex-only metadata that
    # can cause a generic 400 from OpenAI-compatible gateways.
    if isinstance(body.get("tools"), list):
        flattened = []
        for tool in body["tools"]:
            item = sanitize_tool(tool)
            if isinstance(item, list):
                flattened.extend(item)
            elif isinstance(item, dict):
                flattened.append(item)
        body["tools"] = flattened

    return json.dumps(body, separators=(",", ":")).encode("utf-8")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("GROQ_PROXY " + (fmt % args), flush=True)

    def do_GET(self):
        if self.path == "/health":
            payload = b'{"ok":true}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        self.send_error(404)

    def do_POST(self):
        parsed = urlsplit(self.path)
        if parsed.path != "/v1/responses":
            self.send_error(404)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length)
            payload = sanitize_body(raw)
            target = urlsplit(UPSTREAM)
            path = target.path.rstrip("/") + "/responses"
            conn = http.client.HTTPSConnection(target.hostname, target.port or 443, timeout=900)
            headers = {
                "Authorization": "Bearer " + API_KEY,
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
                "User-Agent": "cardiag-codex-groq-proxy/1.0",
                "Content-Length": str(len(payload)),
            }
            conn.request("POST", path, body=payload, headers=headers)
            resp = conn.getresponse()
            self.send_response(resp.status)
            for key, value in resp.getheaders():
                low = key.lower()
                if low in {"transfer-encoding", "connection", "content-length", "content-encoding"}:
                    continue
                self.send_header(key, value)
            self.send_header("Connection", "close")
            self.end_headers()
            while True:
                chunk = resp.read(64 * 1024)
                if not chunk:
                    break
                self.wfile.write(chunk)
                self.wfile.flush()
            conn.close()
        except Exception as exc:
            print(f"GROQ_PROXY_ERROR {type(exc).__name__}: {exc}", flush=True)
            try:
                payload = json.dumps({"error": {"message": str(exc), "type": "proxy_error"}}).encode()
                self.send_response(502)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
            except Exception:
                pass


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"GROQ_PROXY_READY=127.0.0.1:{PORT}", flush=True)
    server.serve_forever()
