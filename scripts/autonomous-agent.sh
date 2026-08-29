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

MODEL="${OLLAMA_MODEL:-qwen2.5-coder:3b}"
OLLAMA_BASE="${OLLAMA_API_BASE:-http://127.0.0.1:11434}"
export OLLAMA_API_BASE="$OLLAMA_BASE"
export AIDER_MODEL="ollama_chat/$MODEL"
export AIDER_YES_ALWAYS=true AIDER_AUTO_COMMITS=false AIDER_DIRTY_COMMITS=false
export AIDER_ANALYTICS=false AIDER_CHECK_UPDATE=false AIDER_STREAM=true AIDER_PRETTY=false

# The task manifest is intentionally executable as policy: these are the only files
# the model receives in chat. This prevents Aider from constructing a repo-wide map.
ALLOWED_FILES=(
  "android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantService.kt"
  "android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantProviders.kt"
)
for file in "${ALLOWED_FILES[@]}"; do
  test -f "$ROOT/$file" || { echo "Allowed task file is missing: $file" >&2; exit 11; }
done

BASELINE_FILE="$(mktemp)"
PROMPT_FILE="$(mktemp)"
trap 'rm -f "$BASELINE_FILE" "$PROMPT_FILE"' EXIT

git status --short > "$BASELINE_FILE"
if grep -qE '^.M|^ M|^A |^ D|^D |^R |^C |^\?\?' "$BASELINE_FILE"; then
  echo "Working tree must be clean before the autonomous edit." >&2
  cat "$BASELINE_FILE" >&2
  exit 12
fi

command -v ollama >/dev/null || { echo "Ollama is not installed." >&2; exit 20; }
command -v aider >/dev/null || { echo "Aider is not installed." >&2; exit 21; }
curl -fsS "$OLLAMA_BASE/api/tags" >/tmp/cardiag-ollama-tags.json || { echo "Ollama server is not reachable." >&2; exit 22; }
grep -q 'qwen2.5-coder:3b' /tmp/cardiag-ollama-tags.json || { echo "Required Ollama model is not available: $MODEL" >&2; exit 23; }

PROMPT="$(cat "$TASK_FILE")

--- CURRENT AGENT STATE ---
$(cat "$STATE_FILE")

--- REPOSITORY RULES ---
$(cat "$AGENTS_FILE")

--- STRICT 3B EXECUTION CONTRACT ---
You are Qwen2.5-Coder 3B operating as a constrained autonomous coding agent.

The task manifest above is the ONLY task. Do not choose another backlog item.
The two paths listed in the manifest are the ONLY files you may edit.
They are the ONLY project source files supplied to your chat context.
Do not ask Aider to add, read, or edit any other source/config file.
Do not create any new file.
Do not modify documentation, tests, Gradle, manifests, resources, or unrelated code.

IMPLEMENTATION:
- Inspect only the supplied two files and the task manifest.
- Implement the acceptance criteria exactly and minimally.
- Reuse dependencies already present in the project; do not add dependencies.
- Preserve public interfaces and existing behavior unless the task explicitly requires a change.
- Never fabricate OSM/business data. Only map fields actually returned by the API.
- Use bounded network timeouts and safe failure handling.
- Do not store location data.
- Do not implement hazards in this cycle.

QUALITY GATE:
- Make a small targeted diff.
- Review the final diff before stopping.
- Do not commit.
- If the task cannot be completed safely within the two-file scope, STOP without speculative changes and state the blocker.
"
printf '%s\n' "$PROMPT" > "$PROMPT_FILE"

echo "AI_PROVIDER=ollama"
echo "AI_MODEL=$MODEL"
echo "AGENT_SCOPE=exactly-2-files"
echo "AGENT_TASK_FILE=$TASK_FILE"
echo "Starting deterministic autonomous engineering cycle"

aider \
  --model "ollama_chat/$MODEL" \
  --message-file "$PROMPT_FILE" \
  --yes-always \
  --no-auto-commits \
  --no-dirty-commits \
  --no-analytics \
  --no-check-update \
  --no-pretty \
  --map-tokens 128 \
  --max-chat-history-tokens 4000 \
  --no-gitignore \
  "${ALLOWED_FILES[@]}"

# Hard security/quality gate: reject any modification outside the two declared files.
CHANGED_FILES="$(git diff --name-only && git ls-files --others --exclude-standard)"
if [ -n "$CHANGED_FILES" ]; then
  while IFS= read -r changed; do
    [ -z "$changed" ] && continue
    allowed=0
    for file in "${ALLOWED_FILES[@]}"; do
      if [ "$changed" = "$file" ]; then allowed=1; break; fi
    done
    if [ "$allowed" -ne 1 ]; then
      echo "UNAUTHORIZED_FILE_CHANGE=$changed" >&2
      git diff --name-status >&2 || true
      exit 30
    fi
  done <<< "$CHANGED_FILES"
fi

if ! git diff --check; then
  echo "git diff --check failed." >&2
  exit 31
fi

if git diff --quiet -- "${ALLOWED_FILES[@]}"; then
  echo "AGENT_NO_CODE_CHANGE=1"
  echo "The model completed without a code change; validation/commit must not treat this as an improvement."
  exit 32
fi

# Print a compact audit trail for the workflow log without dumping the whole diff.
echo "AGENT_CHANGED_FILES:"
git diff --name-status -- "${ALLOWED_FILES[@]}"
echo "AGENT_DIFF_STATS:"
git diff --stat -- "${ALLOWED_FILES[@]}"
echo "AI_PROVIDER_SUCCESS=ollama/$MODEL"
echo "Ollama/Aider deterministic autonomous engineering cycle completed successfully."
