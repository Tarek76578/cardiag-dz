#!/usr/bin/env bash
set -euo pipefail

: "${OPENROUTER_API_KEY:?OPENROUTER_API_KEY is required}"
export CODEX_HOME="${CODEX_HOME:-$PWD/.codex}"
mkdir -p "$CODEX_HOME"

MISSION_FILE="$PWD/docs/agent-professional-transformation-mission.md"
REQUIREMENTS_FILE="$PWD/docs/agent-current-user-requirements.md"
if [ ! -f "$MISSION_FILE" ]; then
  echo "Missing agent mission: $MISSION_FILE" >&2
  exit 1
fi
if [ ! -f "$REQUIREMENTS_FILE" ]; then
  echo "Missing current user requirements: $REQUIREMENTS_FILE" >&2
  exit 1
fi
PROMPT="$(cat "$MISSION_FILE")

--- CURRENT USER REQUIREMENTS (MANDATORY ADDENDUM) ---
$(cat "$REQUIREMENTS_FILE")"

# Keep the configured candidates in order. Provider outages are handled
# separately from genuine engineering failures so CI can still validate and
# preserve verified repository work when an AI provider is temporarily down.
models=(
  "openrouter/free"
  "z-ai/glm-5.2:free"
  "minimax/minimax-m3:free"
  "nvidia/nemotron-3-ultra-550b-a55b:free"
  "openai/gpt-oss-120b"
)
max_attempts_per_model="${OPENROUTER_MAX_ATTEMPTS_PER_MODEL:-1}"
delay="${OPENROUTER_INITIAL_DELAY:-20}"
log_file="${RUNNER_TEMP:-/tmp}/codex-agent.log"
ai_unavailable=false

for model in "${models[@]}"; do
  cat > "$CODEX_HOME/config.toml" <<EOF
model = "$model"
model_provider = "openrouter"
approval_policy = "never"
sandbox_mode = "danger-full-access"

[model_providers.openrouter]
name = "OpenRouter"
base_url = "https://openrouter.ai/api/v1"
env_key = "OPENROUTER_API_KEY"
EOF

  for attempt in $(seq 1 "$max_attempts_per_model"); do
    echo "Starting autonomous Codex cycle with $model (attempt $attempt/$max_attempts_per_model)"
    set +e
    : > "$log_file"
    codex exec --dangerously-bypass-approvals-and-sandbox --skip-git-repo-check "$PROMPT" 2>&1 | tee "$log_file"
    status=${PIPESTATUS[0]}
    set -e

    if [ "$status" -eq 0 ]; then
      exit 0
    fi

    if grep -Eqi '(^|[^0-9])(400|404|408|409|429|500|502|503|504)([^0-9]|$)|Not Found|unavailable for free|Too Many Requests|rate limit|rate-limited|temporarily unavailable|capacity|provider.*unavailable|no available provider|Server tool request failed|unexpected argument|tool request.*bad request|HTTP 400|status: 400' "$log_file"; then
      echo "OpenRouter/model availability or compatibility failure for $model; moving to the next candidate." >&2
      ai_unavailable=true
      sleep "$delay"
      break
    fi

    echo "Codex failed for a non-recoverable reason; refusing to repeat the engineering cycle." >&2
    exit "$status"
  done
done

if [ "$ai_unavailable" = true ]; then
  echo "AI_PROVIDER_UNAVAILABLE=true" > "$PWD/.ai-provider-status"
  echo "All configured OpenRouter models/router were unavailable, rate-limited, or incompatible." >&2
  echo "Continuing to CI validation so genuine Android/project failures remain visible." >&2
  exit 0
fi

echo "Autonomous agent ended without a successful model execution." >&2
exit 1
