#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
TASK_FILE="$ROOT/docs/agent-next-task.md"
STATE_FILE="$ROOT/agent-state.md"
AGENTS_FILE="$ROOT/AGENTS.md"
for file in "$TASK_FILE" "$STATE_FILE" "$AGENTS_FILE"; do test -f "$file" || { echo "Missing agent input: $file" >&2; exit 10; }; done
PROVIDER="${CODEX_PROVIDER:-${AI_PROVIDER:-openrouter}}"
MODEL="${AI_MODEL:-}"
BASE_URL="${CODEX_BASE_URL:-https://openrouter.ai/api/v1}"
ENV_KEY="${CODEX_ENV_KEY:-OPEN_ROUTER_API_KEY}"
WIRE_API="${CODEX_WIRE_API:-responses}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"
mkdir -p "$CODEX_HOME"; chmod 700 "$CODEX_HOME"
case "$PROVIDER" in
  openrouter)
    export OPEN_ROUTER_API_KEY="${OPEN_ROUTER_API_KEY:-}"
    test -n "$OPEN_ROUTER_API_KEY" || { echo "OPEN_ROUTER_API_KEY is missing" >&2; exit 13; }
    ;;
  *) echo "Unsupported CODEX_PROVIDER=$PROVIDER; this project uses OpenRouter." >&2; exit 14;;
esac
case "$WIRE_API" in responses) ;; *) echo "Unsupported CODEX_WIRE_API=$WIRE_API; Codex requires Responses API for this agent." >&2; exit 15;; esac
test -n "$MODEL" || { echo "AI_MODEL was not selected by the OpenRouter model discovery step." >&2; exit 16; }
echo "AI_AGENT=codex"; echo "AI_PROVIDER=$PROVIDER"; echo "AI_MODEL=$MODEL"; echo "CODEX_BASE_URL=$BASE_URL"; echo "CODEX_ENV_KEY=$ENV_KEY"; echo "CODEX_WIRE_API=$WIRE_API"; echo "CODEX_HOME=$CODEX_HOME"
test -z "$(git status --porcelain)" || { echo "Working tree must be clean before the autonomous edit." >&2; git status --short >&2; exit 12; }
PROMPT_FILE="$(mktemp)"; trap 'rm -f "$PROMPT_FILE"' EXIT
cat > "$PROMPT_FILE" <<'EOF'
Execute only the current CarDiag milestone.
Read docs/agent-next-task.md, agent-state.md, and AGENTS.md. Inspect the repository before editing. Implement the highest-priority real CarDiag task defined there. Preserve Arabic RTL/French, least privilege, bounded timeouts, no location persistence/background tracking, and do not invent automotive facts, diagnostic procedures, prices, service availability or test results.
Add or update regression tests for every behavioral change. Validate the affected code and Android build. Update agent-state.md with actual evidence. Review the complete diff. Do not commit or push; the workflow handles the verified commit.
Work autonomously and make the smallest complete implementation. Prefer repository-native tools and shell commands over speculative planning. If a provider/API cannot be verified, implement a safe interface/fallback rather than fabricating data.
Keep the implementation focused: do not perform broad repository rewrites or unrelated refactors. Once the requested change and its tests are complete, stop.
EOF
command -v codex >/dev/null 2>&1 || { echo "Codex CLI is not installed." >&2; exit 21; }
codex --version
# OpenRouter is used as an OpenAI-compatible Responses provider. Keep the model explicitly pinned
# by the workflow's preflight and configure conservative request limits so the free/low-credit
# route cannot inherit Codex's 65536-token default.
codex exec --ephemeral --color never \
  -c "model=\"$MODEL\"" \
  -c "model_provider=\"$PROVIDER\"" \
  -c "model_providers.$PROVIDER.name=\"$PROVIDER\"" \
  -c "model_providers.$PROVIDER.base_url=\"$BASE_URL\"" \
  -c "model_providers.$PROVIDER.wire_api=\"$WIRE_API\"" \
  -c "model_providers.$PROVIDER.env_key=\"$ENV_KEY\"" \
  -c "model_providers.$PROVIDER.request_max_retries=1" \
  -c "model_providers.$PROVIDER.stream_max_retries=1" \
  -c "model_providers.$PROVIDER.supports_websockets=false" \
  -c "model_providers.$PROVIDER.requires_openai_auth=true" \
  -c "model_context_window=32768" \
  -c "project_doc_max_bytes=0" \
  -c "web_search=\"disabled\"" \
  --sandbox danger-full-access --skip-git-repo-check "$(cat "$PROMPT_FILE")" < /dev/null
CHANGED_FILES="$(git diff --name-only && git ls-files --others --exclude-standard)"
if [ -z "$CHANGED_FILES" ]; then echo "AGENT_NO_CODE_CHANGE=1"; exit 32; fi
git diff --check
echo "AGENT_CHANGED_FILES:"; git diff --name-status
echo "AGENT_DIFF_STATS:"; git diff --stat
echo "AI_AGENT_SUCCESS=codex"; echo "AI_PROVIDER_SUCCESS=$PROVIDER/$MODEL"
