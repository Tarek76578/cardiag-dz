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

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[proxy] " + (fmt % args) + "\n")

    def _send_json(self, code, data):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if urlsplit(self.path).path == "/v1/models":
            self._send_json(200, b'{"data":[],"proxy":"ready"}')
        else:
            self.send_error(404)

    def do_POST(self):
        if not urlsplit(self.path).path.startswith("/v1/"):
            self.send_error(404); return
        try:
            n = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(n))
        except (ValueError, json.JSONDecodeError, UnicodeDecodeError):
            self._send_json(400, b'{"error":{"message":"invalid JSON"}}'); return
        if not isinstance(body, dict):
            self._send_json(400, b'{"error":{"message":"JSON object required"}}'); return

        for field in ("max_output_tokens", "max_tokens"):
            value = body.get(field)
            if isinstance(value, int) and value > MAX_TOKENS:
                body[field] = MAX_TOKENS
                print(f"TOKEN_CLAMP field={field} original={value} effective={MAX_TOKENS}", flush=True)

        upstream_path = self.path[3:]
        req = Request(
            UPSTREAM + upstream_path,
            data=json.dumps(body).encode(), method="POST",
            headers={
                "Authorization": f"Bearer {KEY}",
                "Content-Type": "application/json",
                "Accept": self.headers.get("Accept", "application/json"),
                "HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz",
                "X-Title": "CarDiag Autonomous Agent",
            })
        try:
            with urlopen(req, timeout=TIMEOUT) as r:
                content_type = r.headers.get("Content-Type", "application/json")
                self.send_response(r.status)
                self.send_header("Content-Type", content_type)
                self.send_header("Cache-Control", "no-cache")
                self.send_header("Connection", "close")
                self.send_header("Transfer-Encoding", "chunked")
                self.end_headers()
                while True:
                    chunk = r.read(16384)
                    if not chunk: break
                    self.wfile.write(f"{len(chunk):X}\r\n".encode())
                    self.wfile.write(chunk)
                    self.wfile.write(b"\r\n")
                    self.wfile.flush()
                self.wfile.write(b"0\r\n\r\n"); self.wfile.flush()
        except HTTPError as e:
            data = e.read()
            self._send_json(e.code, data)
        except (URLError, TimeoutError) as e:
            try: self._send_json(502, json.dumps({"error":{"message":str(e)}}).encode())
            except BrokenPipeError: pass

if __name__ == "__main__":
    if not KEY: raise SystemExit("OPEN_ROUTER_API_KEY is required")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
