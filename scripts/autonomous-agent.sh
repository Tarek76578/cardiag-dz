#!/usr/bin/env bash
set -euo pipefail

: "${OPENROUTER_API_KEY:?OPENROUTER_API_KEY is required}"
export CODEX_HOME="${CODEX_HOME:-$PWD/.codex}"
mkdir -p "$CODEX_HOME"

MISSION_FILE="$PWD/docs/agent-professional-transformation-mission.md"
if [ ! -f "$MISSION_FILE" ]; then
  echo "Missing agent mission: $MISSION_FILE" >&2
  exit 1
fi
PROMPT="$(cat "$MISSION_FILE")"

models=(
  "openrouter/free"
  "z-ai/glm-5.2:free"
  "minimax/minimax-m3:free"
  "nvidia/nemotron-3-ultra-550b-a55b:free"
  "openai/gpt-oss-120b:free"
)
max_attempts_per_model="${OPENROUTER_MAX_ATTEMPTS_PER_MODEL:-1}"
delay="${OPENROUTER_INITIAL_DELAY:-20}"
log_file="${RUNNER_TEMP:-/tmp}/codex-agent.log"

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
      echo "OpenRouter/model availability or compatibility failure for $model; moving to the next free candidate." >&2
      sleep "$delay"
      break
    fi

    echo "Codex failed for a non-recoverable reason; refusing to repeat the engineering cycle." >&2
    exit "$status"
  done
done

echo "All configured OpenRouter free models/router were unavailable, rate-limited, or incompatible." >&2
exit 1
