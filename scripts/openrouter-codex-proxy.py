#!/usr/bin/env python3
import json, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

UPSTREAM = os.environ.get("OPENROUTER_UPSTREAM", "https://openrouter.ai/api/v1")
KEY = os.environ.get("OPEN_ROUTER_API_KEY", "")
MAX_TOKENS = int(os.environ.get("OPENROUTER_PROXY_MAX_TOKENS", "12000"))
TIMEOUT = int(os.environ.get("OPENROUTER_PROXY_TIMEOUT", "300"))
TOKEN_FIELDS = {"max_output_tokens", "max_tokens"}

def clamp_tokens(value, path="root"):
    changed = []
    if isinstance(value, dict):
        for key in list(value):
            if key in TOKEN_FIELDS and isinstance(value[key], (int, float)) and not isinstance(value[key], bool):
                original = value[key]
                if original > MAX_TOKENS:
                    value[key] = MAX_TOKENS
                    changed.append((path + "." + key, original, MAX_TOKENS))
            elif key not in TOKEN_FIELDS:
                changed.extend(clamp_tokens(value[key], path + "." + key))
    elif isinstance(value, list):
        for i, item in enumerate(value):
            changed.extend(clamp_tokens(item, f"{path}[{i}]") )
    return changed

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, fmt, *args):
        sys.stderr.write("[proxy] " + (fmt % args) + "\n")

    def send_bytes(self, code, data, content_type="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers(); self.wfile.write(data); self.wfile.flush()

    def do_GET(self):
        if urlsplit(self.path).path == "/v1/models":
            self.send_bytes(200, b'{"data":[],"proxy":"ready"}')
        else:
            self.send_error(404)

    def do_POST(self):
        path = urlsplit(self.path).path
        if not path.startswith("/v1/"):
            self.send_error(404); return
        try:
            n = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(n))
            if not isinstance(body, dict): raise ValueError("JSON object required")
        except Exception as e:
            self.send_bytes(400, json.dumps({"error":{"message":f"invalid JSON: {e}"}}).encode()); return

        changes = clamp_tokens(body)
        # Critical: Codex 0.151.0 can omit max_output_tokens entirely. In that case
        # OpenRouter applies the selected model's default/maximum, which is what caused
        # the observed 65536-token affordability error. Force an explicit affordable
        # Responses budget at the HTTP boundary so Codex cannot bypass the limit.
        if path == "/v1/responses" and "max_output_tokens" not in body and "max_tokens" not in body:
            body["max_output_tokens"] = MAX_TOKENS
            print(f"TOKEN_INJECT field=max_output_tokens original=absent effective={MAX_TOKENS}", flush=True)
        for field, original, effective in changes:
            print(f"TOKEN_CLAMP field={field} original={original} effective={effective}", flush=True)
        if path == "/v1/responses":
            if isinstance(body.get("max_output_tokens"), (int, float)) and body["max_output_tokens"] > MAX_TOKENS:
                body["max_output_tokens"] = MAX_TOKENS
                print(f"TOKEN_CLAMP field=max_output_tokens original=post-clamp effective={MAX_TOKENS}", flush=True)
            if isinstance(body.get("max_tokens"), (int, float)) and body["max_tokens"] > MAX_TOKENS:
                body["max_tokens"] = MAX_TOKENS
                print(f"TOKEN_CLAMP field=max_tokens original=post-clamp effective={MAX_TOKENS}", flush=True)
            assert not (isinstance(body.get("max_output_tokens"), (int,float)) and body["max_output_tokens"] > MAX_TOKENS)
            assert not (isinstance(body.get("max_tokens"), (int,float)) and body["max_tokens"] > MAX_TOKENS)

        upstream_path = self.path[3:]
        payload = json.dumps(body, separators=(",", ":")).encode()
        req = Request(UPSTREAM + upstream_path, data=payload, method="POST", headers={
            "Authorization": f"Bearer {KEY}", "Content-Type": "application/json",
            "Accept": self.headers.get("Accept", "application/json"),
            "HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz",
            "X-Title": "CarDiag Autonomous Agent"})
        try:
            with urlopen(req, timeout=TIMEOUT) as r:
                ctype = r.headers.get("Content-Type", "application/json")
                self.send_response(r.status)
                self.send_header("Content-Type", ctype)
                self.send_header("Cache-Control", "no-cache")
                self.send_header("Connection", "close")
                self.send_header("Transfer-Encoding", "chunked")
                self.end_headers()
                while True:
                    chunk = r.read(16384)
                    if not chunk: break
                    self.wfile.write(f"{len(chunk):X}\r\n".encode()); self.wfile.write(chunk); self.wfile.write(b"\r\n"); self.wfile.flush()
                self.wfile.write(b"0\r\n\r\n"); self.wfile.flush()
        except HTTPError as e:
            self.send_bytes(e.code, e.read(), e.headers.get("Content-Type", "application/json"))
        except (URLError, TimeoutError) as e:
            self.send_bytes(502, json.dumps({"error":{"message":str(e)}}).encode())

if __name__ == "__main__":
    if not KEY: raise SystemExit("OPEN_ROUTER_API_KEY is required")
    if MAX_TOKENS <= 0: raise SystemExit("OPENROUTER_PROXY_MAX_TOKENS must be positive")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
