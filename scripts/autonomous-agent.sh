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
WIRE_API="responses"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"
mkdir -p "$CODEX_HOME"
chmod 700 "$CODEX_HOME"

PROXY_PID=""
PROXY_LOG="/tmp/groq-codex-proxy.log"
cleanup_proxy() {
  if [ -n "$PROXY_PID" ] && kill -0 "$PROXY_PID" 2>/dev/null; then
    kill "$PROXY_PID" 2>/dev/null || true
    wait "$PROXY_PID" 2>/dev/null || true
  fi
}
trap 'cleanup_proxy; rm -f "$PROMPT_FILE"' EXIT

case "$PROVIDER" in
  openrouter)
    export OPENROUTER_API_KEY="${OPENROUTER_API_KEY:-}"
    test -n "$OPENROUTER_API_KEY" || { echo "OPENROUTER_API_KEY is missing" >&2; exit 13; }
    ;;
  groq)
    export GROQ_API_KEY="${GROQ_API_KEY:-}"
    test -n "$GROQ_API_KEY" || { echo "GROQ_API_KEY is missing" >&2; exit 13; }
    test -f "$ROOT/scripts/groq-codex-proxy.py"
    python3 "$ROOT/scripts/groq-codex-proxy.py" >"$PROXY_LOG" 2>&1 &
    PROXY_PID=$!
    for _ in $(seq 1 50); do
      if curl -fsS http://127.0.0.1:8787/health >/dev/null 2>&1; then break; fi
      sleep 0.1
    done
    curl -fsS http://127.0.0.1:8787/health >/dev/null
    BASE_URL="http://127.0.0.1:8787/v1"
    ENV_KEY="GROQ_API_KEY"
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
echo "CODEX_WIRE_API=$WIRE_API"
echo "CODEX_HOME=$CODEX_HOME"

test -z "$(git status --porcelain)" || {
  echo "Working tree must be clean before the autonomous edit." >&2
  git status --short >&2
  exit 12
}

PROMPT_FILE="$(mktemp)"
cat > "$PROMPT_FILE" <<'EOF'
Execute ONLY the current Road Assistant milestone.

Read docs/agent-next-task.md, agent-state.md, and AGENTS.md first. Inspect the existing Road Assistant implementation, tests, networking, localization, and conventions.

Implement only the milestone: a real Overpass nearby-service provider using the existing Ktor/kotlinx.serialization stack. Never invent OSM/business data. Preserve public interfaces, offline fallback semantics, Arabic RTL/French localization, least privilege, bounded timeouts, and no location persistence/background tracking. Do not implement live road hazards.

Add appropriate regression tests. Run relevant tests, lint, debug build, and release validation when feasible; diagnose real failures without weakening tests. Update agent-state.md with actual evidence and blockers. Review the diff for secrets, generated files, regressions, and unrelated changes. Do not commit or push.
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
  -c "model_providers.$PROVIDER.request_max_retries=0" \
  -c "model_providers.$PROVIDER.stream_max_retries=0" \
  -c "model_providers.$PROVIDER.supports_websockets=false" \
  -c "model_providers.$PROVIDER.requires_openai_auth=false" \
  -c "project_doc_max_bytes=1500" \
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
