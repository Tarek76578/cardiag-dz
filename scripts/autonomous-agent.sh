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

PROVIDER="${CODEX_PROVIDER:-${AI_PROVIDER:-openrouter}}"
MODEL="${AI_MODEL:-${OPENROUTER_MODEL:-qwen/qwen3-coder:free}}"
BASE_URL="${CODEX_BASE_URL:-${OPENROUTER_BASE_URL:-https://openrouter.ai/api/v1}}"
ENV_KEY="${CODEX_ENV_KEY:-OPENROUTER_API_KEY}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"
mkdir -p "$CODEX_HOME"
chmod 700 "$CODEX_HOME"

case "$PROVIDER" in
  openrouter)
    export OPENROUTER_API_KEY="${OPENROUTER_API_KEY:-}"
    test -n "$OPENROUTER_API_KEY" || { echo "OPENROUTER_API_KEY is missing" >&2; exit 13; }
    ;;
  groq)
    export GROQ_API_KEY="${GROQ_API_KEY:-}"
    test -n "$GROQ_API_KEY" || { echo "GROQ_API_KEY is missing" >&2; exit 13; }
    ;;
  *)
    echo "Unsupported CODEX_PROVIDER=$PROVIDER" >&2
    exit 14
    ;;
esac

echo "AI_AGENT=codex"
echo "AI_PROVIDER=$PROVIDER"
echo "AI_MODEL=$MODEL"
echo "CODEX_BASE_URL=$BASE_URL"
echo "CODEX_ENV_KEY=$ENV_KEY"
echo "CODEX_HOME=$CODEX_HOME"

test -z "$(git status --porcelain)" || {
  echo "Working tree must be clean before the autonomous edit." >&2
  git status --short >&2
  exit 12
}

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
You may modify any project files required to complete the task.
Do not make unrelated changes.
Keep the implementation minimal, coherent, production-quality, and scoped to the task manifest.
Add or update regression tests when appropriate.
Do not commit or push; the workflow handles repository commits.
Preserve public interfaces and existing behavior unless explicitly required.
Never fabricate OSM/business data. Only map fields actually returned by the API.
Use bounded network timeouts and safe failure handling. Do not store location data. Do not implement hazards unless explicitly required by the task manifest.
Before finishing, review git diff and leave only intended changes required by the task.
EOF

command -v codex >/dev/null 2>&1 || { echo "Codex CLI is not installed." >&2; exit 21; }
codex --version

# Exactly one provider is selected by the workflow. Codex retries are disabled so
# a rate-limit response cannot fan out into repeated quota-consuming requests.
codex exec --ephemeral --color never \
  -c "model=\"$MODEL\"" \
  -c "model_provider=\"$PROVIDER\"" \
  -c "model_providers.$PROVIDER.name=\"$PROVIDER\"" \
  -c "model_providers.$PROVIDER.base_url=\"$BASE_URL\"" \
  -c "model_providers.$PROVIDER.wire_api=\"responses\"" \
  -c "model_providers.$PROVIDER.env_key=\"$ENV_KEY\"" \
  -c "model_providers.$PROVIDER.request_max_retries=0" \
  -c "model_providers.$PROVIDER.stream_max_retries=0" \
  -c "model_providers.$PROVIDER.supports_websockets=false" \
  -c "model_providers.$PROVIDER.requires_openai_auth=false" \
  --sandbox danger-full-access \
  --skip-git-repo-check \
  "$(cat "$PROMPT_FILE")" < /dev/null

CHANGED_FILES="$(git diff --name-only && git ls-files --others --exclude-standard)"
if [ -z "$CHANGED_FILES" ]; then
  echo "AGENT_NO_CODE_CHANGE=1"
  exit 32
fi

git diff --check

echo "AGENT_CHANGED_FILES:"
git diff --name-status
echo "AGENT_DIFF_STATS:"
git diff --stat
echo "AI_AGENT_SUCCESS=codex"
echo "AI_PROVIDER_SUCCESS=$PROVIDER/$MODEL"
