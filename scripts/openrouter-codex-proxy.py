#!/usr/bin/env python3
import json, os, re, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

UPSTREAM = os.environ.get("OPENROUTER_UPSTREAM", "https://openrouter.ai/api/v1")
KEY = os.environ.get("OPEN_ROUTER_API_KEY", "")
MAX_TOKENS = int(os.environ.get("OPENROUTER_PROXY_MAX_TOKENS", "12000"))
MIN_TOKENS = int(os.environ.get("OPENROUTER_PROXY_MIN_TOKENS", "1024"))
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
            changed.extend(clamp_tokens(item, f"{path}[{i}]"))
    return changed


def set_budget(body, budget):
    if "max_output_tokens" in body and isinstance(body["max_output_tokens"], (int, float)):
        body["max_output_tokens"] = budget
    elif "max_tokens" in body and isinstance(body["max_tokens"], (int, float)):
        body["max_tokens"] = budget
    else:
        body["max_output_tokens"] = budget


def affordability_error(data):
    if isinstance(data, bytes):
        raw = data.decode("utf-8", errors="replace")
    elif isinstance(data, str):
        raw = data
    else:
        raw = json.dumps(data, ensure_ascii=False, default=str)
    for pattern in (
        r"can only afford\s+([0-9]+)",
        r"afford\s+([0-9]+)\s+tokens",
        r"only afford\s+([0-9]+)",
    ):
        match = re.search(pattern, raw, re.I)
        if match:
            return int(match.group(1))
    return None


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[proxy] " + (fmt % args) + "\n")

    def send_bytes(self, code, data, content_type="application/json"):
        if not isinstance(data, bytes):
            data = str(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", content_type or "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(data)
        self.wfile.flush()

    def do_GET(self):
        if urlsplit(self.path).path == "/v1/models":
            self.send_bytes(200, b'{"data":[],"proxy":"ready"}')
        else:
            self.send_bytes(404, b'{"error":{"message":"not found"}}')

    def do_POST(self):
        path = urlsplit(self.path).path
        if not path.startswith("/v1/"):
            self.send_bytes(404, b'{"error":{"message":"not found"}}')
            return
        try:
            n = int(self.headers.get("Content-Length", "0"))
            raw_body = self.rfile.read(n)
            body = json.loads(raw_body)
            if not isinstance(body, dict):
                raise ValueError("JSON object required")
        except Exception as exc:
            self.send_bytes(400, json.dumps({"error": {"message": f"invalid JSON: {exc}"}}).encode())
            return

        changes = clamp_tokens(body)
        if path == "/v1/responses" and "max_output_tokens" not in body and "max_tokens" not in body:
            body["max_output_tokens"] = MAX_TOKENS
            print(f"TOKEN_INJECT field=max_output_tokens original=absent effective={MAX_TOKENS}", flush=True)
        for field, original, effective in changes:
            print(f"TOKEN_CLAMP field={field} original={original} effective={effective}", flush=True)

        if path == "/v1/responses":
            for field in TOKEN_FIELDS:
                if isinstance(body.get(field), (int, float)) and body[field] > MAX_TOKENS:
                    original = body[field]
                    body[field] = MAX_TOKENS
                    print(f"TOKEN_CLAMP field={field} original={original} effective={MAX_TOKENS}", flush=True)

        def upstream_request(payload):
            upstream_path = self.path[3:] if self.path.startswith("/v1/") else self.path
            req = Request(UPSTREAM + upstream_path, data=json.dumps(payload, separators=(",", ":")).encode(), method="POST", headers={
                "Authorization": f"Bearer {KEY}",
                "Content-Type": "application/json",
                "Accept": self.headers.get("Accept", "application/json"),
                "HTTP-Referer": "https://github.com/Tarek76578/cardiag-dz",
                "X-Title": "CarDiag Autonomous Agent",
            })
            return urlopen(req, timeout=TIMEOUT)

        try:
            response = upstream_request(body)
        except HTTPError as exc:
            data = exc.read()
            affordable = affordability_error(data)
            current = body.get("max_output_tokens", body.get("max_tokens"))
            if exc.code == 402 and affordable is not None and affordable >= MIN_TOKENS and isinstance(current, (int, float)) and affordable < current:
                retry_budget = max(MIN_TOKENS, affordable - 256)
                if retry_budget < current:
                    set_budget(body, retry_budget)
                    print(f"TOKEN_BUDGET_RETRY original={current} affordable={affordable} effective={retry_budget}", flush=True)
                    try:
                        response = upstream_request(body)
                    except HTTPError as retry_error:
                        self.send_bytes(retry_error.code, retry_error.read(), retry_error.headers.get("Content-Type", "application/json"))
                        return
                else:
                    self.send_bytes(exc.code, data, exc.headers.get("Content-Type", "application/json"))
                    return
            else:
                self.send_bytes(exc.code, data, exc.headers.get("Content-Type", "application/json"))
                return
        except (URLError, TimeoutError) as exc:
            self.send_bytes(502, json.dumps({"error": {"message": str(exc)}}).encode())
            return
        except Exception as exc:
            self.send_bytes(502, json.dumps({"error": {"message": str(exc)}}).encode())
            return

        try:
            with response as upstream:
                content_type = upstream.headers.get("Content-Type", "application/json")
                self.send_response(upstream.status)
                self.send_header("Content-Type", content_type)
                self.send_header("Cache-Control", "no-cache")
                self.send_header("Connection", "close")
                self.send_header("Transfer-Encoding", "chunked")
                self.end_headers()
                while True:
                    chunk = upstream.read(16384)
                    if not chunk:
                        break
                    self.wfile.write(f"{len(chunk):X}\r\n".encode())
                    self.wfile.write(chunk)
                    self.wfile.write(b"\r\n")
                    self.wfile.flush()
                self.wfile.write(b"0\r\n\r\n")
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as exc:
            print(f"PROXY_STREAM_ERROR {type(exc).__name__}: {exc}", file=sys.stderr, flush=True)


if __name__ == "__main__":
    if not KEY:
        raise SystemExit("OPEN_ROUTER_API_KEY is required")
    if MAX_TOKENS <= 0 or MIN_TOKENS <= 0 or MIN_TOKENS > MAX_TOKENS:
        raise SystemExit("Invalid token budget configuration")
    ThreadingHTTPServer(("127.0.0.1", 8787), Handler).serve_forever()
