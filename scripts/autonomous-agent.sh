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

MODEL="${OLLAMA_MODEL:-qwen2.5-coder:3b}"
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

if ! grep -q 'qwen2.5-coder:3b' /tmp/cardiag-ollama-tags.json; then
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

--- STRICT AUTONOMOUS ENGINEERING DIRECTIVE ---
Continue from the existing repository state. Do not restart the project and do not discard valid previous work.

Select ONE highest-value unfinished requirement from agent-state.md and the mission. Do not attempt multiple major features in one cycle. First inspect the repository and identify only the smallest set of files relevant to that requirement. Then implement that requirement with small, concrete, verifiable changes.

You are operating through local Ollama using Qwen2.5-Coder 3B and Aider. Make the actual repository edits now. Do not merely describe code, propose patches, or provide instructions. Do not commit; GitHub Actions will validate and commit verified changes.

STRICT SCOPE:
- Work on ONE requirement only.
- Inspect before editing.
- Keep the change set small and focused.
- Do not add unrelated files to the chat/context.
- Do not rewrite or regenerate large catalogs, documentation, or unrelated source files.
- Preserve existing architecture and working functionality unless the selected requirement explicitly requires a change.
- Never fabricate GPS locations, businesses, diagnostic results, vehicle data, prices, images, APIs, or external-service responses.
- Never expose, print, create, or modify secrets/credentials.
- Do not invent dependencies when an existing project dependency can be used.
- Do not remove existing functionality merely to make the task easier.
- Do not claim a feature is complete unless the implementation is present and verified.
- If the requirement is too broad for this cycle, implement only the safest concrete subset and record the exact remaining work in agent-state.md.
- If you cannot safely implement the requirement, make no speculative changes and record the blocker in agent-state.md.

QUALITY GATE:
- Review the complete git diff before finishing.
- Keep the diff reasonably small.
- Check Kotlin/Android/Gradle compatibility for affected code.
- Look for compile errors, duplicated logic, broken imports, and inconsistent state.
- Update agent-state.md with exact completed work and exact remaining work.
- Do not commit.
"

PROMPT_FILE="$(mktemp)"
trap 'rm -f "$PROMPT_FILE"' EXIT
printf '%s\n' "$PROMPT" >"$PROMPT_FILE"

echo "AI_PROVIDER=ollama"
echo "AI_MODEL=$MODEL"
echo "Starting autonomous Ollama/Aider engineering cycle"

aider \
  --model "ollama_chat/$MODEL" \
  --message-file "$PROMPT_FILE" \
  --yes-always \
  --no-auto-commits \
  --no-dirty-commits \
  --no-analytics \
  --no-check-update \
  --no-pretty \
  --map-tokens 1024 \
  --max-chat-history-tokens 12000

status=$?
if [ "$status" -ne 0 ]; then
  echo "Ollama/Aider autonomous engineering cycle failed with exit code $status." >&2
  exit "$status"
fi

echo "AI_PROVIDER_SUCCESS=ollama/$MODEL"
echo "Ollama/Aider autonomous engineering cycle completed successfully."
