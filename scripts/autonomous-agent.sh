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

if ! command -v gemini >/dev/null 2>&1; then
  echo "Gemini CLI is not installed." >&2
  exit 22
fi

echo "Gemini CLI version: $(gemini --version)"
echo "Validating Gemini API through the official Gemini CLI: $MODEL"

set +e
validation_output="$(gemini -m "$MODEL" -p 'Reply with exactly GEMINI_READY' --output-format json --approval-mode=yolo 2>&1)"
validation_status=$?
set -e

if [ "$validation_status" -ne 0 ]; then
  echo "Gemini API validation failed with exit code $validation_status." >&2
  printf '%s\n' "$validation_output" | head -c 5000 >&2
  exit 21
fi

if ! printf '%s' "$validation_output" | grep -q 'GEMINI_READY'; then
  echo "Gemini CLI responded, but the expected completion was not received." >&2
  printf '%s\n' "$validation_output" | head -c 5000 >&2
  exit 21
fi

echo "Gemini API validation succeeded: $MODEL"
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
