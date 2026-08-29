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

MODEL="${AIDER_MODEL:-openrouter/qwen/qwen3-coder:free}"
export AIDER_MODEL="$MODEL"
export AIDER_YES_ALWAYS=true
export AIDER_AUTO_COMMITS=false
export AIDER_DIRTY_COMMITS=false
export AIDER_ANALYTICS=false
export AIDER_CHECK_UPDATE=false
export AIDER_STREAM=true
export AIDER_PRETTY=false

if [ -z "${OPENROUTER_API_KEY:-}" ]; then
  echo "OPENROUTER_API_KEY is not configured." >&2
  exit 20
fi
if ! command -v aider >/dev/null 2>&1; then
  echo "Aider is not installed." >&2
  exit 21
fi

PROMPT="$(cat "$MISSION_FILE")

--- CURRENT USER REQUIREMENTS (MANDATORY ADDENDUM) ---
$(cat "$REQUIREMENTS_FILE")

--- EXPLICIT USER-PRIORITY PRODUCT REQUIREMENTS (MANDATORY) ---
$(cat "$USER_PRIORITY_FILE")

--- CURRENT PROJECT STATE ---
$(cat "$STATE_FILE")

--- EXECUTION DIRECTIVE ---
Continue from the existing repository state. Do not restart the project and do not discard valid previous work. This is an autonomous engineering cycle, not an audit. Select ONE highest-value unfinished requirement from agent-state.md and the mission. Implement that requirement only, using small, verifiable changes.

You are operating through OpenRouter with Aider. Make actual repository edits now. Do not merely describe code or provide instructions. Do not commit; GitHub Actions will validate and commit verified changes.

STRICT SCOPE RULES:
- Inspect the repository and identify the smallest set of files relevant to the selected requirement before editing.
- Do not add unrelated files to the Aider chat.
- Do not rewrite or regenerate large catalogs, documentation, or unrelated source files.
- Preserve existing architecture and working functionality unless the selected requirement requires a change.
- Never fabricate GPS, businesses, diagnostic results, vehicle data, prices, or image identity.
- Never expose secrets.
- Do not claim a feature is complete unless it is implemented and verified.
- If the requirement is too broad for one cycle, implement the safest concrete subset and record exact remaining work in agent-state.md.

VERIFICATION RULES:
- Review git diff before finishing.
- Keep the diff focused and reasonably small.
- Check Kotlin/Android/Gradle compatibility for affected code.
- Update agent-state.md with exact completed work and remaining work.
- Do not commit.
"

PROMPT_FILE="$(mktemp)"
trap 'rm -f "$PROMPT_FILE"' EXIT
printf '%s\n' "$PROMPT" >"$PROMPT_FILE"

echo "AI_PROVIDER=openrouter"
echo "AI_MODEL=$MODEL"
echo "Starting autonomous OpenRouter/Aider engineering cycle"

set +e
aider \
  --model "$MODEL" \
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
set -e

if [ "$status" -ne 0 ]; then
  echo "OpenRouter/Aider autonomous engineering cycle failed with exit code $status." >&2
  exit "$status"
fi

echo "AI_PROVIDER_SUCCESS=openrouter/$MODEL"
echo "OpenRouter/Aider autonomous engineering cycle completed successfully."
