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
    exit 10
  fi
done

MODEL="${OLLAMA_MODEL:-qwen2.5-coder:7b}"
OLLAMA_BASE="${OLLAMA_API_BASE:-http://127.0.0.1:11434}"
export OLLAMA_API_BASE="$OLLAMA_BASE"
export AIDER_MODEL="ollama_chat/$MODEL"
export AIDER_YES_ALWAYS=true
export AIDER_AUTO_COMMITS=false
export AIDER_DIRTY_COMMITS=false
export AIDER_ANALYTICS=false
export AIDER_CHECK_UPDATE=false
export AIDER_STREAM=true
export AIDER_PRETTY=false

if ! command -v ollama >/dev/null 2>&1; then
  echo "Ollama is not installed." >&2
  exit 20
fi
if ! command -v aider >/dev/null 2>&1; then
  echo "Aider is not installed." >&2
  exit 21
fi

if ! curl -fsS "$OLLAMA_BASE/api/tags" >/tmp/cardiag-ollama-tags.json 2>/dev/null; then
  echo "Ollama server is not reachable at $OLLAMA_BASE." >&2
  exit 22
fi

if ! grep -q 'qwen2.5-coder' /tmp/cardiag-ollama-tags.json; then
  echo "Required Ollama model is not available: $MODEL" >&2
  exit 23
fi

PROMPT="$(cat "$MISSION_FILE")

--- CURRENT USER REQUIREMENTS (MANDATORY ADDENDUM) ---
$(cat "$REQUIREMENTS_FILE")

--- EXPLICIT USER-PRIORITY PRODUCT REQUIREMENTS (MANDATORY) ---
$(cat "$USER_PRIORITY_FILE")

--- CURRENT PROJECT STATE ---
$(cat "$STATE_FILE")

--- EXECUTION DIRECTIVE ---
Continue from the existing repository state. Do not restart the project and do not discard valid previous work. This is an autonomous engineering cycle, not an audit. Select the highest-value unfinished requirement from agent-state.md and the mission, implement real changes, integrate them into the application, inspect the affected code carefully, and update agent-state.md with exact completed and remaining work. Never fabricate GPS, businesses, diagnostic results, vehicle data, prices, or image identity. Never expose secrets. Do not claim a feature is complete unless it is implemented and verified.

You are operating through a local Ollama coding model using Aider. Make the actual repository edits now. Prefer small, verifiable changes. Do not merely describe code or provide instructions: edit the files. Do not commit; the GitHub Actions workflow will validate and commit verified changes.

After implementing the highest-value unfinished requirement, review your diff for correctness, consistency, Android/Kotlin/Gradle compatibility, and preservation of existing functionality. If a requirement cannot be safely completed in this cycle, make the best verified partial improvement and record the exact remaining work in agent-state.md."

PROMPT_FILE="$(mktemp)"
trap 'rm -f "$PROMPT_FILE"' EXIT
printf '%s\n' "$PROMPT" >"$PROMPT_FILE"

echo "AI_PROVIDER=ollama"
echo "AI_MODEL=$MODEL"
echo "Starting autonomous Ollama/Aider engineering cycle"

set +e
aider \
  --model "ollama_chat/$MODEL" \
  --message-file "$PROMPT_FILE" \
  --yes-always \
  --no-auto-commits \
  --no-dirty-commits \
  --no-analytics \
  --no-check-update \
  --no-pretty
status=$?
set -e

if [ "$status" -ne 0 ]; then
  echo "Ollama/Aider autonomous engineering cycle failed with exit code $status." >&2
  exit "$status"
fi

echo "AI_PROVIDER_SUCCESS=ollama/$MODEL"
echo "Ollama/Aider autonomous engineering cycle completed successfully."
