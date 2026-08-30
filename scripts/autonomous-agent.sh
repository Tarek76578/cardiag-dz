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

AI_PROVIDER="${AI_PROVIDER:-ollama}"
MODEL="${OLLAMA_MODEL:-qwen2.5-coder:3b}"

export AIDER_YES_ALWAYS=true AIDER_AUTO_COMMITS=false AIDER_DIRTY_COMMITS=false
export AIDER_ANALYTICS=false AIDER_CHECK_UPDATE=false AIDER_STREAM=true AIDER_PRETTY=false

if [ "$AI_PROVIDER" = "freellmapi" ]; then
  OPENAI_BASE="${OPENAI_API_BASE:-http://127.0.0.1:3002/v1}"
  MODEL="${FREELLMAPI_MODEL:?FREELLMAPI_MODEL is required}"
  export OPENAI_API_BASE="$OPENAI_BASE"
  export OPENAI_API_KEY="${OPENAI_API_KEY:-keyless-local-gateway}"
  AIDER_MODEL="openai/$MODEL"
  export AIDER_MODEL
  echo "AI_PROVIDER=freellmapi"
  echo "AI_MODEL=$MODEL"
  curl -fsS "$OPENAI_BASE/models" >/tmp/cardiag-freellmapi-models.json
  python3 - <<'PY'
import json
with open('/tmp/cardiag-freellmapi-models.json', encoding='utf-8') as f:
    d = json.load(f)
ids = [x.get('id') for x in d.get('data', []) if x.get('id')]
if not ids:
    raise SystemExit('FreeLLMAPI compatibility endpoint returned no models')
print('FREELLMAPI_VISIBLE_MODELS=', len(ids))
PY
elif [ "$AI_PROVIDER" = "ollama" ]; then
  MODEL="${OLLAMA_MODEL:-qwen2.5-coder:3b}"
  OLLAMA_BASE="${OLLAMA_API_BASE:-http://127.0.0.1:11434}"
  export OLLAMA_API_BASE="$OLLAMA_BASE"
  AIDER_MODEL="ollama_chat/$MODEL"
  export AIDER_MODEL
  command -v ollama >/dev/null || { echo "Ollama is not installed." >&2; exit 20; }
  curl -fsS "$OLLAMA_BASE/api/tags" >/tmp/cardiag-ollama-tags.json || { echo "Ollama server is not reachable." >&2; exit 22; }
  grep -q "$MODEL" /tmp/cardiag-ollama-tags.json || { echo "Required Ollama model is not available: $MODEL" >&2; exit 23; }
  echo "AI_PROVIDER=ollama"
  echo "AI_MODEL=$MODEL"
else
  echo "Unsupported AI_PROVIDER=$AI_PROVIDER" >&2
  exit 24
fi

command -v aider >/dev/null || { echo "Aider is not installed." >&2; exit 21; }

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

PROMPT="$(cat "$TASK_FILE")

--- CURRENT AGENT STATE ---
$(cat "$STATE_FILE")

--- REPOSITORY RULES ---
$(cat "$AGENTS_FILE")

--- AUTONOMOUS EXECUTION CONTRACT ---
You are the coding model operating through $AI_PROVIDER.

The task manifest above is the ONLY task. Do not choose another backlog item.
The two paths listed in the manifest are the ONLY files you may edit in this cycle.
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

echo "AGENT_SCOPE=exactly-2-files"
echo "AGENT_TASK_FILE=$TASK_FILE"
echo "Starting deterministic autonomous engineering cycle"

aider \
  --model "$AIDER_MODEL" \
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

echo "AGENT_CHANGED_FILES:"
git diff --name-status -- "${ALLOWED_FILES[@]}"
echo "AGENT_DIFF_STATS:"
git diff --stat -- "${ALLOWED_FILES[@]}"
echo "AI_PROVIDER_SUCCESS=$AI_PROVIDER/$MODEL"
echo "FreeLLMAPI/Aider deterministic autonomous engineering cycle completed successfully."
