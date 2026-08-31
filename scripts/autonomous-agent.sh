#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
for file in docs/agent-next-task.md agent-state.md AGENTS.md; do test -f "$file" || { echo "Missing $file" >&2; exit 10; }; done
PROVIDER="cardiag_gateway"
BASE_URL="${CODEX_BASE_URL:-http://127.0.0.1:8787/v1}"
ENV_KEY="${CODEX_ENV_KEY:-CARDIAG_GATEWAY_KEY}"
WIRE_API="${CODEX_WIRE_API:-responses}"
MAX_CYCLES="${AI_AGENT_MAX_CYCLES:-24}"
export CARDIAG_GATEWAY_KEY="${CARDIAG_GATEWAY_KEY:-local-gateway-key}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"; mkdir -p "$CODEX_HOME"; chmod 700 "$CODEX_HOME"
test "$WIRE_API" = responses || { echo "Responses API is required" >&2; exit 15; }
command -v codex >/dev/null 2>&1 || { echo "Codex CLI is not installed" >&2; exit 21; }
test -n "${OPEN_ROUTER_API_KEY:-}${GROQ_API_KEY:-}${DEEPSEEK_API_KEY:-}" || { echo "No AI provider API key is configured" >&2; exit 13; }
ATTEMPTED="${RUNNER_TEMP:-/tmp}/cardiag-agent-models.txt"
CHECKPOINT="${RUNNER_TEMP:-/tmp}/cardiag-agent-checkpoint"
mkdir -p "$CHECKPOINT"
: > "$ATTEMPTED"
PROMPT="${RUNNER_TEMP:-/tmp}/cardiag-agent-prompt"
cat >"$PROMPT" <<'EOF'
You have exactly ONE task in this cycle: the current GPS + interactive map milestone in docs/agent-next-task.md.
Implement only that task. Do not implement another CarDiag feature or unrelated refactoring.
The repository Android project is under android/. The persistent agent state file is ROOT/agent-state.md (not docs/agent-state.md).
IMPORTANT: this is a continuation cycle. Existing uncommitted code changes are intentional work from previous AI cycles. Inspect and preserve them; never reset, discard, clean, or overwrite previous work merely to start fresh.
Read ROOT/agent-state.md, docs/agent-next-task.md, and the current working tree first. Continue from the highest-value unfinished point.
Make the smallest complete production-quality change, add focused tests where practical, run relevant tests/lint/build, fix real failures, update ROOT/agent-state.md with factual evidence, and review the diff.
If the milestone cannot be completely finished in this cycle, implement the highest-value safe portion and record precisely what remains in ROOT/agent-state.md. Never claim unfinished work is complete.
Before ending the cycle, update ROOT/agent-state.md with a concise handoff: completed work, files changed, validation performed, remaining work, and the next concrete action.
Only when the GPS + interactive map milestone is genuinely implemented and its required validation has passed, add this exact line to ROOT/agent-state.md:
GPS_MAP_MILESTONE=COMPLETE
If it is not genuinely complete, do NOT add that line.
Do not commit or push; the workflow handles verified commits. Stop after this single task.
EOF

next_candidate() {
  python3 - "$ATTEMPTED" <<'PY'
import json, os, sys, urllib.request
attempted=set(open(sys.argv[1], encoding='utf-8').read().splitlines())
key=os.environ.get('OPEN_ROUTER_API_KEY','')
if not key: raise SystemExit('NO_OPENROUTER_KEY_FOR_DISCOVERY')
req=urllib.request.Request('https://openrouter.ai/api/v1/models',headers={'Authorization':'Bearer '+key,'Accept':'application/json'})
with urllib.request.urlopen(req, timeout=30) as r: data=json.load(r)
c=[]
for x in data.get('data',[]):
    if not isinstance(x,dict): continue
    mid=str(x.get('id','')); p=x.get('pricing') or {}; sp=x.get('supported_parameters') or []
    if not mid.endswith(':free') or mid in attempted: continue
    if str(p.get('prompt','')) not in ('0','0.0','0.000000'): continue
    if str(p.get('completion','')) not in ('0','0.0','0.000000'): continue
    if 'tools' not in sp and 'tool_choice' not in sp: continue
    ctx=int(x.get('context_length') or 0)
    if ctx < 32768: continue
    c.append((ctx,mid))
if not c: raise SystemExit('NO_UNTRIED_FREE_CODEX_CANDIDATE')
c.sort(key=lambda z:(-z[0],z[1]))
print(c[0][1])
PY
}

start_gateway() {
  local model="$1"
  if [ -f /tmp/gateway.pid ] && kill -0 "$(cat /tmp/gateway.pid)" 2>/dev/null; then
    kill "$(cat /tmp/gateway.pid)" 2>/dev/null || true
    sleep 1
  fi
  export AI_PROVIDER=openrouter AI_MODEL="$model" OPENROUTER_MODEL="$model"
  python3 scripts/ai-gateway.py >"${RUNNER_TEMP:-/tmp}/cardiag-ai-gateway-codex.log" 2>&1 &
  echo $! >/tmp/gateway.pid
  for i in $(seq 1 30); do
    curl -fsS --max-time 1 http://127.0.0.1:8787/health >"${RUNNER_TEMP:-/tmp}/gateway-codex-health.json" 2>/dev/null && return 0
    sleep 1
  done
  return 1
}

save_checkpoint() {
  local reason="$1"
  echo "AI_AGENT_CHECKPOINT=$reason"
  git status --short
  git diff --check || true
  mkdir -p "$CHECKPOINT"
  cp -f agent-state.md "$CHECKPOINT/agent-state.md" || true
  git diff --binary >"$CHECKPOINT/working-tree.patch" || true
  git diff --name-status >"$CHECKPOINT/working-tree.files" || true
  printf '%s\n' "$reason" >"$CHECKPOINT/reason"
}

validate_completion() {
  echo "AI_AGENT_COMPLETION_GATE=START"
  grep -Fxq 'GPS_MAP_MILESTONE=COMPLETE' agent-state.md || {
    echo "AI_AGENT_COMPLETION_GATE=INCOMPLETE_MARKER_MISSING"
    return 1
  }
  test -d android || { echo "AI_AGENT_COMPLETION_GATE=ANDROID_DIR_MISSING"; return 1; }
  if [ -x android/gradlew ]; then GRADLE=(./gradlew); else GRADLE=(gradle); fi
  (
    cd android
    "${GRADLE[@]}" --no-daemon testDebugUnitTest lintDebug assembleDebug
  )
  git diff --check
  echo "AI_AGENT_COMPLETION_GATE=PASS"
  return 0
}

for cycle in $(seq 1 "$MAX_CYCLES"); do
  if grep -Fxq 'GPS_MAP_MILESTONE=COMPLETE' agent-state.md; then
    validate_completion && { echo "AI_AGENT_SUCCESS=checkpoint-already-complete"; exit 0; }
    sed -i '/^GPS_MAP_MILESTONE=COMPLETE$/d' agent-state.md
  fi

  MODEL="$(next_candidate)" || {
    echo "AI_AGENT_NO_FREE_MODEL=1"
    save_checkpoint "no-untried-free-model"
    exit 40
  }
  echo "$MODEL" >> "$ATTEMPTED"
  echo "AI_AGENT_CYCLE=$cycle/$MAX_CYCLES"
  echo "AI_AGENT_MODEL=$MODEL"

  if ! start_gateway "$MODEL"; then
    echo "AI_AGENT_GATEWAY_START_FAILED=$MODEL" >&2
    save_checkpoint "gateway-start-failed"
    continue
  fi

  if python3 - <<'PY'
import json,os,sys,urllib.request
try:
    h=json.load(urllib.request.urlopen('http://127.0.0.1:8787/health',timeout=5))
    p=h.get('providers',{}).get('openrouter',{})
    ok=bool(p.get('configured') and p.get('codex_compatible') and p.get('model') == os.environ['AI_MODEL'])
except Exception as e:
    print('AI_AGENT_GATEWAY_ROUTE_ERROR='+str(e)); sys.exit(1)
print('AI_AGENT_GATEWAY_ROUTE='+('PASS' if ok else 'FAIL'))
sys.exit(0 if ok else 1)
PY
  then :; else save_checkpoint "gateway-route-failed"; continue; fi

  export CODEX_PROVIDER="$PROVIDER" CODEX_BASE_URL="$BASE_URL" CODEX_ENV_KEY="$ENV_KEY" CODEX_WIRE_API="$WIRE_API" AI_MODEL="$MODEL"
  echo "CODEX_EFFECTIVE_PROVIDER=$PROVIDER"
  echo "CODEX_EFFECTIVE_MODEL=$MODEL"
  set +e
  codex exec --ephemeral --color never \
    -c "model=\"$MODEL\"" -c "model_provider=\"$PROVIDER\"" \
    -c "model_providers.$PROVIDER.name=\"$PROVIDER\"" -c "model_providers.$PROVIDER.base_url=\"$BASE_URL\"" \
    -c "model_providers.$PROVIDER.wire_api=\"$WIRE_API\"" -c "model_providers.$PROVIDER.env_key=\"$ENV_KEY\"" \
    -c "model_providers.$PROVIDER.request_max_retries=1" -c "model_providers.$PROVIDER.stream_max_retries=1" \
    -c "model_context_window=32768" -c "project_doc_max_bytes=0" -c "web_search=\"disabled\"" \
    --sandbox danger-full-access --skip-git-repo-check "$PROMPT" < /dev/null 2>&1 | tee "${RUNNER_TEMP:-/tmp}/cardiag-codex-cycle-$cycle.log"
  rc=${PIPESTATUS[0]}
  set -e

  if [ "$rc" -eq 0 ]; then
    if validate_completion; then
      echo "AI_AGENT_SUCCESS=codex"
      echo "AI_PROVIDER_SUCCESS=$PROVIDER/$MODEL"
      exit 0
    fi
    save_checkpoint "codex-finished-but-completion-gate-failed"
    echo "AI_AGENT_ROTATING_AFTER_INCOMPLETE=$MODEL"
    continue
  fi

  if grep -Eiq 'rate limit|rate_limit|HTTP 429|status 429|403 Forbidden|404 Not Found|503 Service Unavailable|provider_exhausted|All compatible AI providers failed|temporarily unavailable|context window|maximum context|token limit|quota exceeded|capacity' "${RUNNER_TEMP:-/tmp}/cardiag-codex-cycle-$cycle.log"; then
    save_checkpoint "provider-limit-or-context"
    echo "AI_AGENT_ROTATING_AFTER_PROVIDER_LIMIT=$MODEL"
    continue
  fi

  save_checkpoint "provider-error"
  echo "AI_AGENT_ROTATING_AFTER_PROVIDER_ERROR=$MODEL rc=$rc"
done

echo "AI_AGENT_EXHAUSTED_FREE_MODELS=1"
echo "Existing working-tree changes and agent-state.md were preserved for inspection."
save_checkpoint "cycle-budget-exhausted"
exit 41
