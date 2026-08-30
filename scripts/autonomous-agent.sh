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

MODEL="${FREELLMAPI_MODEL:-qwen/qwen3-coder:free}"
CODEX_PROVIDER="${CODEX_PROVIDER:-codex_shim}"
CODEX_BASE_URL="${CODEX_BASE_URL:-http://127.0.0.1:8787/v1}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"
export GATEWAY_KEY="${GATEWAY_KEY:-${OPENAI_API_KEY:-}}"
test -n "$GATEWAY_KEY" || { echo "GATEWAY_KEY is missing" >&2; exit 13; }

echo "AI_AGENT=codex"
echo "AI_PROVIDER=freellmapi-via-codex-shim"
echo "AI_MODEL=$MODEL"

# Do not require a model catalog from the shim. Some shim versions intentionally
# expose an empty /models catalog while their Responses endpoint is ready.
SHIM_BASE="${CODEX_BASE_URL%/v1}"
if curl -fsS --max-time 10 "$SHIM_BASE/health" >/tmp/cardiag-codex-shim-health.json 2>/dev/null; then
  echo "CODEX_SHIM_HEALTH=ok"
elif curl -fsS --max-time 10 "$SHIM_BASE/v1/models" >/tmp/cardiag-codex-shim-models.json 2>/dev/null; then
  echo "CODEX_SHIM_HTTP=ok"
else
  echo "Codex shim is not reachable at $SHIM_BASE" >&2
  exit 14
fi

echo "CODEX_SHIM_READY=1"

ALLOWED_FILES=(
  "android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantService.kt"
  "android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantProviders.kt"
)
for file in "${ALLOWED_FILES[@]}"; do
  test -f "$ROOT/$file" || { echo "Allowed task file is missing: $file" >&2; exit 11; }
done

test -z "$(git status --porcelain)" || { echo "Working tree must be clean before the autonomous edit." >&2; git status --short >&2; exit 12; }

PROMPT_FILE="$(mktemp)"
trap 'rm -f "$PROMPT_FILE"' EXIT
cat > "$PROMPT_FILE" <<EOF
$(cat "$TASK_FILE")

--- CURRENT AGENT STATE ---
$(cat "$STATE_FILE")

--- REPOSITORY RULES ---
$(cat "$AGENTS_FILE")

--- AUTONOMOUS EXECUTION CONTRACT ---
You are the CarDiag coding agent running in GitHub Actions through Codex.
The task manifest above is the ONLY task. Do not choose another backlog item.
ONLY these two project files may be edited:
- android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantService.kt
- android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantProviders.kt
Do not create, delete, rename, or modify any other project file. Do not commit or push.
Implement the acceptance criteria exactly and minimally. Reuse existing dependencies; do not add dependencies.
Preserve public interfaces and existing behavior unless explicitly required.
Never fabricate OSM/business data. Only map fields actually returned by the API.
Use bounded network timeouts and safe failure handling. Do not store location data. Do not implement hazards in this cycle.
Before finishing, review git diff and leave only intended changes in the two allowed files.
EOF

command -v codex >/dev/null 2>&1 || { echo "Codex CLI is not installed." >&2; exit 21; }
codex --version

codex exec --ephemeral --color never \
  -c "model=\"$MODEL\"" \
  -c "model_provider=\"$CODEX_PROVIDER\"" \
  -c 'model_providers.codex_shim.name="CarDiag Codex Responses Shim"' \
  -c "model_providers.codex_shim.base_url=\"$CODEX_BASE_URL\"" \
  -c 'model_providers.codex_shim.wire_api="responses"' \
  -c 'model_providers.codex_shim.request_max_retries=0' \
  -c 'model_providers.codex_shim.stream_max_retries=0' \
  -c 'model_providers.codex_shim.supports_websockets=false' \
  -c 'model_providers.codex_shim.env_key="GATEWAY_KEY"' \
  --sandbox danger-full-access \
  --skip-git-repo-check \
  "$(cat "$PROMPT_FILE")" < /dev/null

CHANGED_FILES="$(git diff --name-only && git ls-files --others --exclude-standard)"
while IFS= read -r changed; do
  [ -z "$changed" ] && continue
  case "$changed" in
    "android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantService.kt"|"android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantProviders.kt") ;;
    *) echo "UNAUTHORIZED_FILE_CHANGE=$changed" >&2; git diff --name-status >&2 || true; exit 30 ;;
  esac
done <<< "$CHANGED_FILES"

git diff --check
if git diff --quiet -- "${ALLOWED_FILES[@]}"; then echo "AGENT_NO_CODE_CHANGE=1"; exit 32; fi

echo "AGENT_CHANGED_FILES:"
git diff --name-status -- "${ALLOWED_FILES[@]}"
echo "AGENT_DIFF_STATS:"
git diff --stat -- "${ALLOWED_FILES[@]}"
echo "AI_AGENT_SUCCESS=codex"
echo "AI_PROVIDER_SUCCESS=freellmapi/$MODEL"
