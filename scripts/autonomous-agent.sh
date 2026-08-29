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
export GEMINI_API_KEY_AUTH_MECHANISM="x-goog-api-key"
unset GOOGLE_GENAI_USE_VERTEXAI GOOGLE_GENAI_USE_GCA GOOGLE_APPLICATION_CREDENTIALS
unset GOOGLE_CLOUD_PROJECT GOOGLE_CLOUD_PROJECT_ID GOOGLE_CLOUD_LOCATION GOOGLE_API_KEY

mkdir -p "$HOME/.gemini"
cat > "$HOME/.gemini/settings.json" <<'JSON'
{
  "security": {
    "auth": {
      "selectedType": "gemini-api-key",
      "enforcedType": "gemini-api-key"
    }
  }
}
JSON

echo "Gemini CLI auth mode: gemini-api-key"
echo "Gemini API key transport: x-goog-api-key"

if ! command -v gemini >/dev/null 2>&1; then
  echo "Gemini CLI is not installed." >&2
  exit 22
fi

echo "Gemini CLI version: $(gemini --version)"

# Validate the Secret independently of Gemini CLI. This removes CLI auth state
# from the diagnosis: the same GEMINI_API_KEY is sent exactly as Google's
# Gemini API expects, using x-goog-api-key. The response body is retained only
# for a short, redacted diagnostic and the key is never printed.
echo "Validating Gemini API key directly against Generative Language API: $MODEL"
validation_body="$(mktemp)"
trap 'rm -f "$validation_body"' EXIT
set +e
validation_http="$(curl -sS -o "$validation_body" -w '%{http_code}' \
  -H "Content-Type: application/json" \
  -H "x-goog-api-key: ${GEMINI_API_KEY}" \
  --data "{\"contents\":[{\"parts\":[{\"text\":\"Reply with exactly GEMINI_READY\"}]}]}" \
  "https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent")"
validation_curl_status=$?
set -e

if [ "$validation_curl_status" -ne 0 ] || [ "$validation_http" != "200" ]; then
  echo "Gemini API key validation failed (HTTP ${validation_http:-curl-error})." >&2
  # Google error responses are useful for diagnosis; never print headers or the key.
  sed -E 's/AIza[[:alnum:]_-]{20,}/[REDACTED]/g' "$validation_body" | head -c 5000 >&2 || true
  exit 21
fi

if ! grep -q 'GEMINI_READY' "$validation_body"; then
  echo "Gemini API accepted the credentials but did not return the expected completion." >&2
  sed -E 's/AIza[[:alnum:]_-]{20,}/[REDACTED]/g' "$validation_body" | head -c 5000 >&2 || true
  exit 21
fi

echo "Gemini API key validation succeeded: $MODEL"
echo "AI_PROVIDER_SUCCESS=gemini/$MODEL"
echo "Starting autonomous Gemini CLI cycle with gemini/$MODEL"

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
gemini -m "$MODEL" -p "$PROMPT" --output-format stream-json --approval-mode=yolo
status=$?
set -e

if [ "$status" -ne 0 ]; then
  echo "Gemini autonomous engineering cycle failed with exit code $status." >&2
  exit "$status"
fi

echo "AI_PROVIDER_SUCCESS=gemini/$MODEL"
echo "Gemini autonomous engineering cycle completed successfully."
