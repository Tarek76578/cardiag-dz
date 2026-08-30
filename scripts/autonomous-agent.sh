#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
TASK_FILE="$ROOT/docs/agent-next-task.md"
STATE_FILE="$ROOT/agent-state.md"
AGENTS_FILE="$ROOT/AGENTS.md"
for file in "$TASK_FILE" "$STATE_FILE" "$AGENTS_FILE"; do
  test -f "$file" || { echo "Missing agent input: $file" >&2; exit 10; }
done

MODEL="${FREELLMAPI_MODEL:-qwen/qwen3-coder:free}"
CODEX_PROVIDER="${CODEX_PROVIDER:-codex_shim}"
SHIM_UPSTREAM_BASE_URL="${CODEX_BASE_URL:-http://127.0.0.1:8787/v1}"
# Codex 0.151 emits reasoning.encrypted_content on Responses requests. The
# FreeLLMAPI-backed shim rejects that OpenAI-internal include, so place a tiny
# local compatibility filter in front of the shim and strip only unsupported
# request metadata before forwarding. The actual model/tool traffic is kept.
CODEX_BASE_URL="${CODEX_FILTER_BASE_URL:-http://127.0.0.1:8788/v1}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"
export GATEWAY_KEY="${GATEWAY_KEY:-${OPENAI_API_KEY:-}}"
test -n "$GATEWAY_KEY" || { echo "GATEWAY_KEY is missing" >&2; exit 13; }

echo "AI_AGENT=codex"
echo "AI_PROVIDER=freellmapi-via-codex-shim"
echo "AI_MODEL=$MODEL"

echo "Starting Codex compatibility filter: $CODEX_BASE_URL -> $SHIM_UPSTREAM_BASE_URL"
cat >/tmp/cardiag-codex-filter.py <<'PY'
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit
from http.client import HTTPConnection

TARGET = "http://127.0.0.1:8787"

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _forward(self):
        path = self.path
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length) if length else b""
        if self.command == "POST" and body:
            try:
                obj = json.loads(body)
                if isinstance(obj, dict):
                    removed = []
                    if "include" in obj:
                        removed.append("include")
                        obj.pop("include", None)
                    if "client_metadata" in obj:
                        removed.append("client_metadata")
                        obj.pop("client_metadata", None)
                    if removed:
                        print("CODEX_FILTER_STRIPPED=" + ",".join(removed), flush=True)
                    body = json.dumps(obj, separators=(",", ":")).encode()
            except Exception as exc:
                print("CODEX_FILTER_JSON_PASSTHROUGH=" + type(exc).__name__, flush=True)

        parsed = urlsplit(TARGET)
        conn = HTTPConnection(parsed.hostname, parsed.port, timeout=95)
        headers = {}
        for k, v in self.headers.items():
            if k.lower() not in {"host", "content-length", "connection"}:
                headers[k] = v
        headers["Content-Length"] = str(len(body))
        headers["Host"] = f"{parsed.hostname}:{parsed.port}"
        try:
            conn.request(self.command, path, body=body if body else None, headers=headers)
            resp = conn.getresponse()
            self.send_response(resp.status, resp.reason)
            for k, v in resp.getheaders():
                if k.lower() not in {"connection", "transfer-encoding", "content-length"}:
                    self.send_header(k, v)
            data = resp.read()
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            self.wfile.flush()
        except Exception as exc:
            self.send_response(502)
            payload = str(exc).encode()
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        finally:
            conn.close()

    def do_GET(self):
        self._forward()
    def do_POST(self):
        self._forward()
    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Content-Length", "0")
        self.end_headers()
    def log_message(self, *_):
        pass

ThreadingHTTPServer(("127.0.0.1", 8788), Handler).serve_forever()
PY
nohup python3 /tmp/cardiag-codex-filter.py >/tmp/cardiag-codex-filter.log 2>&1 & echo $! >/tmp/cardiag-codex-filter.pid

for i in $(seq 1 20); do
  if curl -fsS --max-time 5 "${CODEX_BASE_URL%/v1}/v1/models" >/tmp/cardiag-codex-filter-models.json 2>/dev/null; then break; fi
  sleep 1
done
if ! curl -fsS --max-time 5 "${CODEX_BASE_URL%/v1}/v1/models" >/dev/null 2>&1; then
  echo "Codex compatibility filter is not reachable at ${CODEX_BASE_URL%/v1}" >&2
  cat /tmp/cardiag-codex-filter.log >&2 || true
  exit 14
fi
echo "CODEX_FILTER_READY=1"

ALLOWED_FILES=(
  "android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantService.kt"
  "android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantProviders.kt"
)
for file in "${ALLOWED_FILES[@]}"; do
  test -f "$ROOT/$file" || { echo "Allowed task file is missing: $file" >&2; exit 11; }
done

test -z "$(git status --porcelain)" || { echo "Working tree must be clean before the autonomous edit." >&2; git status --short >&2; exit 12; }

PROMPT_FILE="$(mktemp)"
trap 'rm -f "$PROMPT_FILE"; [ -f /tmp/cardiag-codex-filter.pid ] && kill "$(cat /tmp/cardiag-codex-filter.pid)" 2>/dev/null || true' EXIT
cat > "$PROMPT_FILE" <<EOF
$(cat "$TASK_FILE")

--- CURRENT AGENT STATE ---
$(cat "$STATE_FILE")

--- REPOSITORY RULES ---
$(cat "$AGENTS_FILE")

--- AUTONOMOUS EXECUTION CONTRACT ---
You are the CarDiag coding agent running in GitHub Actions through Codex.
The task manifest above is the ONLY task. Do not choose another backlog item.
ONLY these two project files may be edited:
- android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantService.kt
- android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantProviders.kt
Do not create, delete, rename, or modify any other project file. Do not commit or push.
Implement the acceptance criteria exactly and minimally. Reuse existing dependencies; do not add dependencies.
Preserve public interfaces and existing behavior unless explicitly required.
Never fabricate OSM/business data. Only map fields actually returned by the API.
Use bounded network timeouts and safe failure handling. Do not store location data. Do not implement hazards in this cycle.
Before finishing, review git diff and leave only intended changes in the two allowed files.
EOF

command -v codex >/dev/null 2>&1 || { echo "Codex CLI is not installed." >&2; exit 21; }
codex --version

codex exec --ephemeral --color never \
  -c "model=\"$MODEL\"" \
  -c "model_provider=\"$CODEX_PROVIDER\"" \
  -c 'model_providers.codex_shim.name="CarDiag Codex Responses Shim"' \
  -c "model_providers.codex_shim.base_url=\"$CODEX_BASE_URL\"" \
  -c 'model_providers.codex_shim.wire_api="responses"' \
  -c 'model_providers.codex_shim.request_max_retries=0' \
  -c 'model_providers.codex_shim.stream_max_retries=0' \
  -c 'model_providers.codex_shim.supports_websockets=false' \
  -c 'model_providers.codex_shim.env_key="GATEWAY_KEY"' \
  --sandbox danger-full-access \
  --skip-git-repo-check \
  "$(cat "$PROMPT_FILE")" < /dev/null

CHANGED_FILES="$(git diff --name-only && git ls-files --others --exclude-standard)"
while IFS= read -r changed; do
  [ -z "$changed" ] && continue
  case "$changed" in
    "android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantService.kt"|"android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantProviders.kt") ;;
    *) echo "UNAUTHORIZED_FILE_CHANGE=$changed" >&2; git diff --name-status >&2 || true; exit 30 ;;
  esac
done <<< "$CHANGED_FILES"

git diff --check
if git diff --quiet -- "${ALLOWED_FILES[@]}"; then echo "AGENT_NO_CODE_CHANGE=1"; exit 32; fi

echo "AGENT_CHANGED_FILES:"
git diff --name-status -- "${ALLOWED_FILES[@]}"
echo "AGENT_DIFF_STATS:"
git diff --stat -- "${ALLOWED_FILES[@]}"
echo "AI_AGENT_SUCCESS=codex"
echo "AI_PROVIDER_SUCCESS=freellmapi/$MODEL"
