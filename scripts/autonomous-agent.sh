#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
for file in docs/agent-next-task.md agent-state.md AGENTS.md; do test -f "$file" || { echo "Missing $file" >&2; exit 10; }; done
PROVIDER="${CODEX_PROVIDER:-openrouter}"; MODEL="${AI_MODEL:-}"
BASE_URL="${CODEX_BASE_URL:-https://openrouter.ai/api/v1}"; ENV_KEY="${CODEX_ENV_KEY:-OPEN_ROUTER_API_KEY}"; WIRE_API="${CODEX_WIRE_API:-responses}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"; mkdir -p "$CODEX_HOME"; chmod 700 "$CODEX_HOME"
test "$PROVIDER" = openrouter || { echo "Only OpenRouter is supported" >&2; exit 14; }
test -n "${OPEN_ROUTER_API_KEY:-}" || { echo "OPEN_ROUTER_API_KEY is missing" >&2; exit 13; }
test "$WIRE_API" = responses || { echo "Responses API is required" >&2; exit 15; }; test -n "$MODEL" || { echo "AI_MODEL is missing" >&2; exit 16; }
command -v codex >/dev/null 2>&1 || { echo "Codex CLI is not installed" >&2; exit 21; }
cat >/tmp/cardiag-agent-prompt <<'EOF'
You have exactly ONE task in this cycle: the current GPS + interactive map milestone in docs/agent-next-task.md.
Implement only that task. Do not implement another CarDiag feature or unrelated refactoring.
The repository Android project is under android/. The persistent agent state file is ROOT/agent-state.md (not docs/agent-state.md).
Inspect relevant existing Android code, make the smallest complete production-quality change, add focused tests where practical, run relevant tests/lint/build, fix real failures, update ROOT/agent-state.md with factual evidence, and review the diff.
If the milestone cannot be completely finished in this cycle, implement the highest-value safe portion and record precisely what remains in ROOT/agent-state.md. Never claim unfinished work is complete.
Do not spend the cycle on broad repository exploration once the relevant GPS/map files are identified. Do not repeatedly inspect unrelated files.
Do not commit or push; the workflow handles verified commits. Stop after this single task.
EOF
# The HTTP gateway is the authoritative output-token boundary for Codex 0.151.0.
echo "CODEX_OUTPUT_GUARD=HTTP_PROXY"
echo "CODEX_PROXY_BUDGET=${OPENROUTER_PROXY_MAX_TOKENS:-12000}"
codex exec --ephemeral --color never \
 -c "model=\"$MODEL\"" -c "model_provider=\"$PROVIDER\"" \
 -c "model_providers.$PROVIDER.name=\"$PROVIDER\"" -c "model_providers.$PROVIDER.base_url=\"$BASE_URL\"" \
 -c "model_providers.$PROVIDER.wire_api=\"$WIRE_API\"" -c "model_providers.$PROVIDER.env_key=\"$ENV_KEY\"" \
 -c "model_providers.$PROVIDER.request_max_retries=1" -c "model_providers.$PROVIDER.stream_max_retries=1" \
 -c "model_context_window=32768" -c "project_doc_max_bytes=0" -c "web_search=\"disabled\"" \
 --sandbox danger-full-access --skip-git-repo-check "$(cat /tmp/cardiag-agent-prompt)" < /dev/null
CHANGED_FILES="$(git diff --name-only && git ls-files --others --exclude-standard)"
test -n "$CHANGED_FILES" || { echo "AGENT_NO_CODE_CHANGE=1"; exit 32; }
git diff --check; git diff --name-status; git diff --stat
echo "AI_AGENT_SUCCESS=codex"; echo "AI_PROVIDER_SUCCESS=$PROVIDER/$MODEL"
