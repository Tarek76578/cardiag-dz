#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MISSION_FILE="$ROOT/docs/agent-professional-transformation-mission.md"
REQUIREMENTS_FILE="$ROOT/docs/agent-current-user-requirements.md"
USER_PRIORITY_FILE="$ROOT/docs/agent-user-priority-requirements.md"
STATE_FILE="$ROOT/agent-state.md"
for file in "$MISSION_FILE" "$REQUIREMENTS_FILE" "$USER_PRIORITY_FILE" "$STATE_FILE" "$ROOT/AGENTS.md"; do
  if [ ! -f "$file" ]; then
    echo "Missing agent input: $file" >&2
    exit 1
  fi
done

if [ -z "${GEMINI_API_KEY:-}" ]; then
  echo "GEMINI_API_KEY is required for the autonomous engineering cycle." >&2
  exit 20
fi

MODEL="${GEMINI_MODEL:-gemini-3.7-flash}"
export GEMINI_MODEL="$MODEL"
export GEMINI_CLI_TRUST_WORKSPACE=true

# Validate Gemini using Google's documented OpenAI-compatible Chat Completions
# endpoint. Do not use curl -f so Google's actual error body remains visible.
echo "Validating Gemini API: $MODEL"
validation_payload="$(python3 - <<'PY'
import json, os
print(json.dumps({
  "model": os.environ.get("GEMINI_MODEL", "gemini-3.7-flash"),
  "messages": [{"role": "user", "content": "Reply with exactly GEMINI_READY"}],
  "max_tokens": 16,
}))
PY
)"
validation_response="$(curl -sS --retry 2 --retry-delay 2 \
  -w '\nHTTP_STATUS:%{http_code}' \
  -X POST "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" \
  -H "Authorization: Bearer ${GEMINI_API_KEY}" \
  -H "Content-Type: application/json" \
  -d "$validation_payload")"

http_status="${validation_response##*HTTP_STATUS:}"
validation_body="${validation_response%$'\nHTTP_STATUS:'*}"
if [ "$http_status" != "200" ]; then
  echo "Gemini API validation failed (HTTP $http_status)." >&2
  printf '%s\n' "$validation_body" | python3 -c 'import json,sys; s=sys.stdin.read();\ntry: print(json.dumps(json.loads(s), indent=2)[:4000])\nexcept Exception: print(s[:4000])'
  exit 21
fi

if ! printf '%s' "$validation_body" | grep -q 'GEMINI_READY'; then
  echo "Gemini API responded, but the expected completion was not received." >&2
  printf '%s\n' "$validation_body" | head -c 4000 >&2
  exit 21
fi

echo "Gemini API validation succeeded: $MODEL"
echo "AI_PROVIDER_SUCCESS=gemini/$MODEL"

echo "Starting autonomous Gemini CLI cycle with gemini/$MODEL"

if ! command -v gemini >/dev/null 2>&1; then
  echo "Gemini CLI is not installed." >&2
  exit 22
fi

echo "Gemini CLI version: $(gemini --version)"

PROMPT="$(cat "$MISSION_FILE")

--- CURRENT USER REQUIREMENTS (MANDATORY ADDENDUM) ---
$(cat "$REQUIREMENTS_FILE")

--- EXPLICIT USER-PRIORITY PRODUCT REQUIREMENTS (MANDATORY) ---
$(cat "$USER_PRIORITY_FILE")

--- CURRENT PROJECT STATE ---
$(cat "$STATE_FILE")

--- EXECUTION DIRECTIVE ---
Continue from the existing repository state. Do not restart the project and do not discard valid previous work. This is an autonomous engineering cycle, not an audit. Select the highest-value unfinished requirement from agent-state.md and the mission, implement real changes, integrate them into the application, run relevant tests/build checks, fix failures, and update agent-state.md with exact completed and remaining work. Never fabricate GPS, businesses, diagnostic results, vehicle data, prices, or image identity. Never expose secrets. Do not claim a feature is complete unless it is implemented and verified."

set +e
gemini -p "$PROMPT" --output-format stream-json --approval-mode yolo
status=$?
set -e

if [ "$status" -ne 0 ]; then
  echo "Gemini autonomous engineering cycle failed with exit code $status." >&2
  exit "$status"
fi

echo "AI_PROVIDER_SUCCESS=gemini/$MODEL"
echo "Gemini autonomous engineering cycle completed successfully."
