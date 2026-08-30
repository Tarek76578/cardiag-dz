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

PROVIDER="${CODEX_PROVIDER:-${AI_PROVIDER:-groq}}"
MODEL="${AI_MODEL:-groq/compound-mini}"
BASE_URL="${CODEX_BASE_URL:-https://api.groq.com/openai/v1}"
ENV_KEY="${CODEX_ENV_KEY:-GROQ_API_KEY}"
WIRE_API="${CODEX_WIRE_API:-chat}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"
mkdir -p "$CODEX_HOME"
chmod 700 "$CODEX_HOME"

case "$PROVIDER" in
  groq)
    export GROQ_API_KEY="${GROQ_API_KEY:-}"
    test -n "$GROQ_API_KEY" || { echo "GROQ_API_KEY is missing" >&2; exit 13; }
    ;;
  *)
    echo "Unsupported CODEX_PROVIDER=$PROVIDER; this project uses Groq only." >&2
    exit 14
    ;;
esac

case "$WIRE_API" in
  chat|responses) ;;
  *) echo "Unsupported CODEX_WIRE_API=$WIRE_API" >&2; exit 15 ;;
esac

echo "AI_AGENT=codex"
echo "AI_PROVIDER=$PROVIDER"
echo "AI_MODEL=$MODEL"
echo "CODEX_BASE_URL=$BASE_URL"
echo "CODEX_ENV_KEY=$ENV_KEY"
echo "CODEX_WIRE_API=$WIRE_API"
echo "CODEX_HOME=$CODEX_HOME"

test -z "$(git status --porcelain)" || {
  echo "Working tree must be clean before the autonomous edit." >&2
  git status --short >&2
  exit 12
}

PROMPT_FILE="$(mktemp)"
trap 'rm -f "$PROMPT_FILE"' EXIT
cat > "$PROMPT_FILE" <<'EOF'
Execute only the current Road Assistant milestone.
Read docs/agent-next-task.md, agent-state.md, and AGENTS.md. Implement the real Overpass nearby-service provider using existing Ktor/kotlinx.serialization. Preserve interfaces/fallback, Arabic RTL/French, least privilege, bounded timeouts, no location persistence/background tracking, and no live hazards.
Add regression tests, validate/build, update agent-state.md with evidence, and review the diff. Do not commit or push.
EOF

command -v codex >/dev/null 2>&1 || { echo "Codex CLI is not installed." >&2; exit 21; }
codex --version

codex exec --ephemeral --color never \
  -c "model=\"$MODEL\"" \
  -c "model_provider=\"$PROVIDER\"" \
  -c "model_providers.$PROVIDER.name=\"$PROVIDER\"" \
  -c "model_providers.$PROVIDER.base_url=\"$BASE_URL\"" \
  -c "model_providers.$PROVIDER.wire_api=\"$WIRE_API\"" \
  -c "model_providers.$PROVIDER.env_key=\"$ENV_KEY\"" \
  -c "model_providers.$PROVIDER.request_max_retries=5" \
  -c "model_providers.$PROVIDER.stream_max_retries=5" \
  -c "model_providers.$PROVIDER.supports_websockets=false" \
  -c "model_providers.$PROVIDER.requires_openai_auth=false" \
  -c "project_doc_max_bytes=0" \
  -c "web_search=\"disabled\"" \
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
