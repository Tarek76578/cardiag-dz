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

AI_PROVIDER="${AI_PROVIDER:-freellmapi}"
MODEL="${FREELLMAPI_MODEL:-qwen/qwen3-coder:free}"
OPENAI_BASE="${OPENAI_API_BASE:-http://127.0.0.1:3002/v1}"
export OPENAI_API_BASE="$OPENAI_BASE"
export OPENAI_API_KEY="${OPENAI_API_KEY:-keyless-local-gateway}"

if [ "$AI_PROVIDER" != "freellmapi" ]; then
  echo "This autonomous GitHub agent is configured for FreeLLMAPI only; got AI_PROVIDER=$AI_PROVIDER" >&2
  exit 20
fi

echo "AI_PROVIDER=freellmapi"
echo "AI_MODEL=$MODEL"
curl -fsS "$OPENAI_BASE/models" >/tmp/cardiag-freellmapi-models.json

python3 - <<'PY'
import json
with open('/tmp/cardiag-freellmapi-models.json', encoding='utf-8') as f:
    d=json.load(f)
ids=[x.get('id') for x in d.get('data',[]) if x.get('id')]
if not ids: raise SystemExit('FreeLLMAPI compatibility endpoint returned no models')
print('FREELLMAPI_VISIBLE_MODELS=',len(ids))
PY

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
You are the CarDiag coding agent operating through FreeLLMAPI.

The task manifest above is the ONLY task. Do not choose another backlog item.
The two paths listed in the manifest are the ONLY files you may edit in this cycle.
Do not create files or modify any other project file.

IMPLEMENTATION:
- Inspect only the supplied two files and the task manifest/state/rules.
- Implement the acceptance criteria exactly and minimally.
- Reuse dependencies already present in the project; do not add dependencies.
- Preserve public interfaces and existing behavior unless explicitly required.
- Never fabricate OSM/business data. Only map fields actually returned by the API.
- Use bounded network timeouts and safe failure handling.
- Do not store location data.
- Do not implement hazards in this cycle.

QUALITY GATE:
- Make a small targeted diff.
- Review the final diff before stopping.
- Do not commit.
- If the task cannot be completed safely within the two-file scope, STOP without speculative changes.
"
printf '%s\n' "$PROMPT" > "$PROMPT_FILE"

echo "AGENT_SCOPE=exactly-2-files"
echo "AGENT_TASK_FILE=$TASK_FILE"
echo "Starting GitHub autonomous engineering cycle"

# GitHub's installed coding agent/CLI is invoked here when available.
if command -v copilot >/dev/null 2>&1; then
  copilot --help >/dev/null 2>&1 || true
  copilot --prompt "$(cat "$PROMPT_FILE")" "${ALLOWED_FILES[@]}"
elif command -v gh >/dev/null 2>&1 && gh extension list 2>/dev/null | grep -qi 'copilot'; then
  gh copilot suggest "$(cat "$PROMPT_FILE")"
else
  echo "GitHub coding agent CLI is not installed on this runner." >&2
  echo "Install/enable the repository's GitHub Agent before running this script." >&2
  exit 21
fi

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
  exit 32
fi

echo "AGENT_CHANGED_FILES:"
git diff --name-status -- "${ALLOWED_FILES[@]}"
echo "AGENT_DIFF_STATS:"
git diff --stat -- "${ALLOWED_FILES[@]}"
echo "AI_PROVIDER_SUCCESS=$AI_PROVIDER/$MODEL"
echo "GitHub autonomous engineering cycle completed successfully."
