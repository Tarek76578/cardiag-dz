#!/usr/bin/env python3
import json, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

UPSTREAM = os.environ.get("OPENROUTER_UPSTREAM", "https://openrouter.ai/api/v1")
KEY = os.environ.get("OPEN_ROUTER_API_KEY", "")
MAX_TOKENS = int(os.environ.get("OPENROUTER_PROXY_MAX_TOKENS", "12000"))
TIMEOUT = int(os.environ.get("OPENROUTER_PROXY_TIMEOUT", "300"))

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        sys.stderr.write("[proxy] " + (fmt % args) + "\n")

    def do_POST(self):
        if not self.path.startswith("/v1/"):
            self.send_error(404)
            return
        n = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(n))
        if isinstance(body, dict) and isinstance(body.get("max_tokens"), int):
            original = body["max_tokens"]
            if original > MAX_TOKENS:
                body["max_tokens"] = MAX_TOKENS
                print(f"TOKEN_CLAMP original={original} effective={MAX_TOKENS}", flush=True)
        upstream_path = self.path[3:]
        req = Request(
            UPSTREAM + upstream_path,
            data=json.dumps(body).encode(), method="POST",
            headers={
                "Authorization": f"Bearer {KEY}",
                "Content-Type": "application/json",
                "HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz",
                "X-Title": "CarDiag Autonomous Agent",
            })
        try:
            with urlopen(req, timeout=TIMEOUT) as r:
                data = r.read()
                self.send_response(r.status)
                self.send_header("Content-Type", r.headers.get("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(data)))
                self.end_headers(); self.wfile.write(data)
        except HTTPError as e:
            data = e.read(); self.send_response(e.code)
            self.send_header("Content-Type", e.headers.get("Content-Type", "application/json"))
            self.send_header("Content-Length", str(len(data)))
            self.end_headers(); self.wfile.write(data)
        except (URLError, TimeoutError) as e:
            self.send_error(502, str(e))

if __name__ == "__main__":
    if not KEY: raise SystemExit("OPEN_ROUTER_API_KEY is required")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
