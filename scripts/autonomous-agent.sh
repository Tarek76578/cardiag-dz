#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MISSION_FILE="$ROOT/docs/agent-professional-transformation-mission.md"
REQUIREMENTS_FILE="$ROOT/docs/agent-current-user-requirements.md"
USER_PRIORITY_FILE="$ROOT/docs/agent-user-priority-requirements.md"
STATE_FILE="$ROOT/agent-state.md"
for file in "$MISSION_FILE" "$REQUIREMENTS_FILE" "$USER_PRIORITY_FILE" "$STATE_FILE" "$ROOT/AGENTS.md"; do
  test -f "$file" || { echo "Missing agent input: $file" >&2; exit 10; }
done

MODEL="${OLLAMA_MODEL:-qwen2.5-coder:3b}"
OLLAMA_BASE="${OLLAMA_API_BASE:-http://127.0.0.1:11434}"
export OLLAMA_API_BASE="$OLLAMA_BASE"
export AIDER_MODEL="ollama_chat/$MODEL"
export AIDER_YES_ALWAYS=true AIDER_AUTO_COMMITS=false AIDER_DIRTY_COMMITS=false
export AIDER_ANALYTICS=false AIDER_CHECK_UPDATE=false AIDER_STREAM=true AIDER_PRETTY=false

command -v ollama >/dev/null || { echo "Ollama is not installed." >&2; exit 20; }
command -v aider >/dev/null || { echo "Aider is not installed." >&2; exit 21; }
curl -fsS "$OLLAMA_BASE/api/tags" >/tmp/cardiag-ollama-tags.json || { echo "Ollama server is not reachable." >&2; exit 22; }
grep -q 'qwen2.5-coder:3b' /tmp/cardiag-ollama-tags.json || { echo "Required Ollama model is not available: $MODEL" >&2; exit 23; }

PROMPT="$(cat "$MISSION_FILE")
--- CURRENT USER REQUIREMENTS ---
$(cat "$REQUIREMENTS_FILE")
--- USER-PRIORITY REQUIREMENTS ---
$(cat "$USER_PRIORITY_FILE")
--- CURRENT PROJECT STATE ---
$(cat "$STATE_FILE")
--- STRICT 3B AGENT DIRECTIVE ---
You are an autonomous coding agent using Qwen2.5-Coder 3B. Execute ONE highest-value unfinished requirement only.

FIRST: inspect the repository structure and the state file. Then identify the smallest safe implementation. Do NOT load or rewrite the whole repository.

FILE SCOPE IS MANDATORY:
- Before editing, choose at most 3 existing source/config files directly required for this ONE task.
- Prefer 1-2 files.
- Do not add those files to chat/context unless they are actually relevant.
- Do not modify any other file.
- If more than 3 files would be required, stop and record a blocker in agent-state.md instead of expanding scope.
- Never generate large catalogs, repeated localization data, mock data, or whole-file rewrites.

IMPLEMENTATION RULES:
- Make actual edits; do not merely explain or propose patches.
- Preserve working architecture and existing functionality.
- Do not invent APIs, dependencies, GPS coordinates, businesses, prices, vehicle data, diagnostic results, or external responses.
- Reuse existing dependencies and patterns.
- Do not touch secrets or credentials.
- Do not change Gradle/build configuration unless it is the selected requirement and is strictly necessary.
- Do not create unrelated documentation.
- Keep the diff minimal; prefer targeted edits over replacing complete files.
- If the task is too broad, implement only a safe, concrete subset and document the remaining work.
- If uncertain about correctness, do not guess; record the blocker.

QUALITY GATE BEFORE FINISHING:
- Inspect git diff and reject your own unrelated/large changes.
- Check imports, types, Android/Kotlin/Gradle compatibility, and duplicated logic.
- Update agent-state.md only with the exact work completed and remaining work.
- Do not commit; the workflow performs validation and commit.
"

PROMPT_FILE="$(mktemp)"
trap 'rm -f "$PROMPT_FILE"' EXIT
printf '%s\n' "$PROMPT" > "$PROMPT_FILE"

# Keep Aider's repository map intentionally tiny. The model must work from explicitly
# selected files rather than receiving the entire 275-file repository as context.
echo "AI_PROVIDER=ollama"
echo "AI_MODEL=$MODEL"
echo "AGENT_SCOPE=max-3-files"
echo "Starting focused autonomous engineering cycle"

aider \
  --model "ollama_chat/$MODEL" \
  --message-file "$PROMPT_FILE" \
  --yes-always \
  --no-auto-commits \
  --no-dirty-commits \
  --no-analytics \
  --no-check-update \
  --no-pretty \
  --map-tokens 512 \
  --max-chat-history-tokens 6000 \
  --no-gitignore

status=$?
if [ "$status" -ne 0 ]; then
  echo "Ollama/Aider autonomous engineering cycle failed with exit code $status." >&2
  exit "$status"
fi

echo "AI_PROVIDER_SUCCESS=ollama/$MODEL"
echo "Ollama/Aider focused autonomous engineering cycle completed successfully."
