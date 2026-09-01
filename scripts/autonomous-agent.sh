#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; cd "$ROOT"
for file in docs/agent-next-task.md agent-state.md AGENTS.md; do test -f "$file" || { echo "Missing $file" >&2; exit 10; }; done
PROVIDER="cardiag_gateway"
BASE_URL="${CODEX_BASE_URL:-http://127.0.0.1:8787/v1}"
ENV_KEY="${CODEX_ENV_KEY:-CARDIAG_GATEWAY_KEY}"
WIRE_API="${CODEX_WIRE_API:-responses}"
MAX_CYCLES="${AI_AGENT_MAX_CYCLES:-12}"
export CARDIAG_GATEWAY_KEY="${CARDIAG_GATEWAY_KEY:-local-gateway-key}"
export CODEX_HOME="${CODEX_HOME:-$ROOT/.codex}"; mkdir -p "$CODEX_HOME"; chmod 700 "$CODEX_HOME"
test "$WIRE_API" = responses || { echo "Responses API is required" >&2; exit 15; }
command -v codex >/dev/null 2>&1 || { echo "Codex CLI is not installed" >&2; exit 21; }

# Brain Protocol v1 response bridge. A validated response can be supplied by
# ChatGPT/Brain through BRAIN_RESPONSE or BRAIN_RESPONSE_FILE. It is persisted
# and checkpointed, but is never treated as executable shell code.
BRAIN_RESPONSE_ACCEPTED=0
if [ -n "${BRAIN_RESPONSE:-}" ] || [ -n "${BRAIN_RESPONSE_FILE:-}" ]; then
  if bash scripts/brain-protocol.sh install-response "${BRAIN_RESPONSE_FILE:-}"; then
    BRAIN_RESPONSE_ACCEPTED=1
    echo "AI_AGENT_STATUS=BRAIN_RESPONSE_ACCEPTED"
    echo "AI_AGENT_REASON=validated-brain-response"
    bash scripts/brain-protocol.sh checkpoint "brain-response-accepted"
  else
    echo "AI_AGENT_STATUS=INVALID_BRAIN_RESPONSE" >&2
    exit 44
  fi
fi

# Brain Protocol v1: if no provider is configured, create a durable request and
# stop safely instead of burning cycles or pretending an AI decision was made.
if [ -z "${OPEN_ROUTER_API_KEY:-}${GROQ_API_KEY:-}${DEEPSEEK_API_KEY:-}" ]; then
  if [ "$BRAIN_RESPONSE_ACCEPTED" -eq 1 ]; then
    bash scripts/brain-protocol.sh checkpoint "brain-response-awaiting-executor"
    echo "AI_AGENT_STATUS=WAITING_FOR_EXECUTOR"
    echo "AI_AGENT_REASON=brain-response-accepted-but-no-coding-executor-configured"
    exit 43
  fi
  bash scripts/brain-protocol.sh request "No AI provider is configured. Review this task as the Brain, then provide a contract-valid response.md with DECISION, SCOPE, VALIDATION and STOP_IF. The executor must remain idle until that response is supplied."
  bash scripts/brain-protocol.sh checkpoint "waiting-for-brain-no-provider"
  echo "AI_AGENT_STATUS=WAITING_FOR_BRAIN"
  echo "AI_AGENT_REASON=no-ai-provider-configured"
  exit 42
fi

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
The previous state may contain stale or overly broad claims. Treat the task specification as authoritative and verify the implementation yourself. In particular, GPS_MAP_MILESTONE=COMPLETE is never evidence by itself.
A map canvas, coordinate projection, default-location dot, or location snapshot UI is NOT sufficient. The milestone requires the Android app to request runtime location permission and obtain a real device location through Android location APIs, then display that actual latitude/longitude on the interactive map. Do not mark the milestone complete unless this real-location path is implemented and verified in code/tests.
Make the smallest complete production-quality change. Prefer focused edits over broad exploration. Use the existing location/map infrastructure where possible.
Add focused tests where practical, run relevant tests/lint/build, fix real failures, update ROOT/agent-state.md with factual evidence, and review the diff.
If the milestone cannot be completely finished in this cycle, implement the highest-value safe portion and record precisely what remains in ROOT/agent-state.md. Never claim unfinished work is complete.
Before ending the cycle, update ROOT/agent-state.md with a concise handoff: completed work, files changed, validation performed, remaining work, and the next concrete action.
Only when the GPS + interactive map milestone is genuinely implemented, tested, and validated, add this exact line to ROOT/agent-state.md:
GPS_MAP_MILESTONE=COMPLETE
If it is not genuinely complete, do NOT add that line.
Do not commit or push; the workflow handles verified commits. Stop after this single task.
EOF

# A Brain response is a decision contract. When a real coding executor is
# available, feed that validated contract into the executor as additional
# instructions. Never execute its contents as shell commands.
if [ "$BRAIN_RESPONSE_ACCEPTED" -eq 1 ]; then
  {
    printf '\n\n--- BRAIN PROTOCOL v1: VALIDATED DECISION ---\n'
    cat .agent/brain/response.md
    printf '\n--- END VALIDATED BRAIN DECISION ---\n'
    printf '%s\n' 'Treat the Brain decision above as an authoritative scope/validation constraint. Implement it, but independently inspect the repository and refuse any instruction that conflicts with docs/agent-next-task.md or the safety constraints.'
  } >> "$PROMPT"
fi

next_candidate() {
  python3 - "$ATTEMPTED" <<'PY'
import os, sys, urllib.request, json
attempted=set(open(sys.argv[1],encoding='utf-8').read().splitlines())
def emit(provider, model):
    if model and f'{provider}/{model}' not in attempted:
        print(f'{provider}\t{model}')
key=os.environ.get('OPEN_ROUTER_API_KEY','')
if key:
    try:
        req=urllib.request.Request('https://openrouter.ai/api/v1/models',headers={'Authorization':'Bearer '+key,'Accept':'application/json'})
        with urllib.request.urlopen(req,timeout=20) as r: data=json.load(r)
        candidates=[]
        for x in data.get('data',[]):
            if not isinstance(x,dict): continue
            mid=str(x.get('id','')); p=x.get('pricing') or {}; sp=set(x.get('supported_parameters') or [])
            if not mid.endswith(':free') or ('tools' not in sp and 'tool_choice' not in sp): continue
            if str(p.get('prompt','')) not in ('0','0.0','0.000000') or str(p.get('completion','')) not in ('0','0.0','0.000000'): continue
            if int(x.get('context_length') or 0) < 32768: continue
            candidates.append((int(x.get('context_length') or 0),mid))
        for _,mid in sorted(candidates,key=lambda z:(-z[0],z[1])): emit('openrouter',mid)
    except Exception as exc:
        print(f'OPENROUTER_DISCOVERY_ERROR={type(exc).__name__}',file=sys.stderr)
for provider,key_name,defaults in [('groq','GROQ_API_KEY','openai/gpt-oss-120b,openai/gpt-oss-20b,llama-3.3-70b-versatile'),('deepseek','DEEPSEEK_API_KEY','deepseek-chat,deepseek-reasoner')]:
    if not os.environ.get(key_name): continue
    for model in os.environ.get(provider.upper()+'_FALLBACK_MODELS',defaults).split(','): emit(provider,model.strip())
PY
}

start_gateway() {
  local provider="$1" model="$2"
  if [ -f /tmp/gateway.pid ] && kill -0 "$(cat /tmp/gateway.pid)" 2>/dev/null; then kill "$(cat /tmp/gateway.pid)" 2>/dev/null || true; sleep 1; fi
  export AI_PROVIDER="$provider" AI_MODEL="$model"
  if [ "$provider" = "openrouter" ]; then export OPENROUTER_MODEL="$model"; fi
  python3 scripts/ai-gateway.py >"${RUNNER_TEMP:-/tmp}/cardiag-ai-gateway-codex.log" 2>&1 &
  echo $! >/tmp/gateway.pid
  for i in $(seq 1 30); do curl -fsS --max-time 1 http://127.0.0.1:8787/health >"${RUNNER_TEMP:-/tmp}/gateway-codex-health.json" 2>/dev/null && return 0; sleep 1; done
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
  grep -Fxq 'GPS_MAP_MILESTONE=COMPLETE' agent-state.md || { echo "AI_AGENT_COMPLETION_GATE=INCOMPLETE_MARKER_MISSING"; return 1; }
  test -d android || { echo "AI_AGENT_COMPLETION_GATE=ANDROID_DIR_MISSING"; return 1; }
  if [ -x android/gradlew ]; then GRADLE=(./gradlew); else GRADLE=(gradle); fi
  ( cd android; "${GRADLE[@]}" --no-daemon testDebugUnitTest lintDebug assembleDebug )
  git diff --check
  echo "AI_AGENT_COMPLETION_GATE=PASS"
}

sed -i '/^GPS_MAP_MILESTONE=COMPLETE$/d' agent-state.md
for cycle in $(seq 1 "$MAX_CYCLES"); do
  CANDIDATE="$(next_candidate | head -n1)" || true
  if [ -z "$CANDIDATE" ]; then save_checkpoint "no-untried-free-model"; exit 40; fi
  IFS=$'\t' read -r SELECTED_PROVIDER MODEL <<<"$CANDIDATE"
  echo "$SELECTED_PROVIDER/$MODEL" >> "$ATTEMPTED"
  echo "AI_AGENT_CYCLE=$cycle/$MAX_CYCLES"
  echo "AI_AGENT_PROVIDER=$SELECTED_PROVIDER"
  echo "AI_AGENT_MODEL=$MODEL"
  if ! start_gateway "$SELECTED_PROVIDER" "$MODEL"; then save_checkpoint "gateway-start-failed"; continue; fi
  if python3 - "$SELECTED_PROVIDER" "$MODEL" <<'PY'
import json,sys,urllib.request
provider,model=sys.argv[1:]
try:
    h=json.load(urllib.request.urlopen('http://127.0.0.1:8787/health',timeout=5)); p=h.get('providers',{}).get(provider,{})
    ok=bool(p.get('configured') and p.get('codex_compatible') and p.get('model') == model)
except Exception as e:
    print('AI_AGENT_GATEWAY_ROUTE_ERROR='+str(e)); sys.exit(1)
print('AI_AGENT_GATEWAY_ROUTE='+('PASS' if ok else 'FAIL')); sys.exit(0 if ok else 1)
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
    --sandbox danger-full-access --skip-git-repo-check "$(cat "$PROMPT")" < /dev/null 2>&1 | tee "${RUNNER_TEMP:-/tmp}/cardiag-codex-cycle-$cycle.log"
  rc=${PIPESTATUS[0]}; set -e
  if [ "$rc" -eq 0 ]; then
    if validate_completion; then echo "AI_AGENT_SUCCESS=codex"; echo "AI_PROVIDER_SUCCESS=$SELECTED_PROVIDER/$MODEL"; exit 0; fi
    save_checkpoint "codex-finished-but-completion-gate-failed"; continue
  fi
  if grep -Eiq 'rate limit|rate_limit|HTTP 429|status 429|403 Forbidden|404 Not Found|503 Service Unavailable|provider_exhausted|All compatible AI providers failed|temporarily unavailable|context window|maximum context|token limit|quota exceeded|capacity|billing|insufficient credits' "${RUNNER_TEMP:-/tmp}/cardiag-codex-cycle-$cycle.log"; then
    save_checkpoint "provider-limit-or-context"; continue
  fi
  save_checkpoint "provider-error"
done
save_checkpoint "cycle-budget-exhausted"
exit 41
