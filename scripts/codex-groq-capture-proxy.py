#!/usr/bin/env python3
import http.client
import json
import os
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOST = "127.0.0.1"
PORT = int(os.environ.get("CAPTURE_PROXY_PORT", "8787"))
UPSTREAM_HOST = "api.groq.com"
CAPTURE = Path(os.environ.get("CAPTURE_FILE", "/tmp/codex-groq-request.json"))
MAX_CAPTURE = 2_000_000


def sanitize(value):
    if isinstance(value, dict):
        out = {}
        for k, v in value.items():
            lk = k.lower()
            if lk in {"authorization", "api_key", "apikey", "key", "token"}:
                out[k] = "[REDACTED]"
            else:
                out[k] = sanitize(v)
        return out
    if isinstance(value, list):
        return [sanitize(v) for v in value]
    return value


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("CAPTURE_PROXY " + (fmt % args), flush=True)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)

        try:
            parsed = json.loads(body)
            captured = sanitize(parsed)
            CAPTURE.write_text(json.dumps(captured, indent=2, ensure_ascii=False)[:MAX_CAPTURE], encoding="utf-8")
            print(f"CAPTURED_REQUEST path={self.path} bytes={len(body)} file={CAPTURE}", flush=True)
            if isinstance(parsed, dict):
                print("CAPTURED_TOP_LEVEL_KEYS=" + ",".join(sorted(parsed.keys())), flush=True)
        except Exception as exc:
            CAPTURE.write_bytes(body[:MAX_CAPTURE])
            print(f"CAPTURED_REQUEST_JSON_PARSE_FAILED={type(exc).__name__}", flush=True)

        headers = {}
        for key in ("Authorization", "Content-Type", "Accept", "OpenAI-Beta", "X-Client-Request-Id"):
            if self.headers.get(key):
                headers[key] = self.headers[key]
        headers["Host"] = UPSTREAM_HOST
        headers["Content-Length"] = str(len(body))
        headers["Connection"] = "close"

        conn = http.client.HTTPSConnection(UPSTREAM_HOST, 443, timeout=180)
        try:
            conn.request("POST", self.path, body=body, headers=headers)
            response = conn.getresponse()
            self.send_response(response.status, response.reason)
            hop_by_hop = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade"}
            for key, value in response.getheaders():
                if key.lower() not in hop_by_hop:
                    self.send_header(key, value)
            self.send_header("Connection", "close")
            self.end_headers()
            while True:
                chunk = response.read(65536)
                if not chunk:
                    break
                self.wfile.write(chunk)
                self.wfile.flush()
            print(f"UPSTREAM_RESPONSE status={response.status} content_type={response.getheader('Content-Type','')}", flush=True)
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
