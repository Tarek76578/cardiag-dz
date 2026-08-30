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

MODEL="${OPENROUTER_MODEL:-openrouter/free}"
CODEX_PROVIDER="${CODEX_PROVIDER:-openrouter}"
OPENROUTER_BASE_URL="${OPENROUTER_BASE_URL:-https://openrouter.ai/api/v1}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"
export OPENROUTER_API_KEY="${OPENROUTER_API_KEY:-}"
test -n "$OPENROUTER_API_KEY" || { echo "OPENROUTER_API_KEY is missing" >&2; exit 13; }

# Codex requires CODEX_HOME to exist before startup. GitHub Actions may export a
# custom path (for example $HOME/.codex-cardiag) without creating it first.
mkdir -p "$CODEX_HOME"

# Keep the agent state isolated from any pre-existing Codex configuration while
# allowing the CLI to create its own config/session files normally.
chmod 700 "$CODEX_HOME"

echo "AI_AGENT=codex"
echo "AI_PROVIDER=openrouter"
echo "AI_MODEL=$MODEL"
echo "OPENROUTER_BASE_URL=$OPENROUTER_BASE_URL"
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
You are the CarDiag coding agent running in GitHub Actions through Codex and OpenRouter.
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

codex exec --ephemeral --color never \
  -c "model=\"$MODEL\"" \
  -c "model_provider=\"$CODEX_PROVIDER\"" \
  -c 'model_providers.openrouter.name="OpenRouter"' \
  -c "model_providers.openrouter.base_url=\"$OPENROUTER_BASE_URL\"" \
  -c 'model_providers.openrouter.wire_api="responses"' \
  -c 'model_providers.openrouter.env_key="OPENROUTER_API_KEY"' \
  -c 'model_providers.openrouter.request_max_retries=2' \
  -c 'model_providers.openrouter.stream_max_retries=2' \
  -c 'model_providers.openrouter.supports_websockets=false' \
  -c 'model_providers.openrouter.requires_openai_auth=false' \
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
echo "AI_PROVIDER_SUCCESS=openrouter/$MODEL"
