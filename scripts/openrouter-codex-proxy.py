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
    def log_message(self, fmt, *args):
        sys.stderr.write("[proxy] " + (fmt % args) + "\n")

    def _send(self, code, data, content_type="application/json"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        # Used only as a local readiness check. Do not proxy arbitrary GETs.
        if urlsplit(self.path).path == "/v1/models":
            self._send(200, b'{"data":[],"proxy":"ready"}')
        else:
            self.send_error(404)

    def do_POST(self):
        if not urlsplit(self.path).path.startswith("/v1/"):
            self.send_error(404); return
        n = int(self.headers.get("Content-Length", "0"))
        try:
            body = json.loads(self.rfile.read(n))
        except (json.JSONDecodeError, UnicodeDecodeError):
            self.send_error(400, "invalid JSON"); return
        if isinstance(body, dict):
            # OpenAI Responses API uses max_output_tokens. Keep max_tokens too for
            # compatibility with providers that use the Chat Completions field.
            for field in ("max_output_tokens", "max_tokens"):
                value = body.get(field)
                if isinstance(value, int) and value > MAX_TOKENS:
                    body[field] = MAX_TOKENS
                    print(f"TOKEN_CLAMP field={field} original={value} effective={MAX_TOKENS}", flush=True)
        upstream_path = self.path[3:]
        req = Request(
            UPSTREAM + upstream_path,
            data=json.dumps(body).encode(), method="POST",
            headers={"Authorization": f"Bearer {KEY}", "Content-Type": "application/json",
                     "HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz",
                     "X-Title": "CarDiag Autonomous Agent"})
        try:
            with urlopen(req, timeout=TIMEOUT) as r:
                data = r.read(); self._send(r.status, data, r.headers.get("Content-Type", "application/json"))
        except HTTPError as e:
            data = e.read(); self._send(e.code, data, e.headers.get("Content-Type", "application/json"))
        except (URLError, TimeoutError) as e:
            self.send_error(502, str(e))

if __name__ == "__main__":
    if not KEY: raise SystemExit("OPEN_ROUTER_API_KEY is required")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
