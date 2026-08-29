#!/usr/bin/env bash
set -euo pipefail

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

# Prefer provider paths that do not depend on OpenRouter's free-model quota.
# GitHub Models uses the workflow GITHUB_TOKEN with models: read permission.
# Gemini/Groq are optional secret-backed fallbacks when configured.
# OpenRouter remains the final fallback, not the primary provider.
candidates=(
  "github|openai/gpt-4.1-mini|https://models.github.ai/inference|GITHUB_TOKEN"
  "gemini|gemini-2.5-flash|https://generativelanguage.googleapis.com/v1beta/openai/|GEMINI_API_KEY"
  "groq|openai/gpt-oss-120b|https://api.groq.com/openai/v1|GROQ_API_KEY"
  "openrouter|openrouter/free|https://openrouter.ai/api/v1|OPENROUTER_API_KEY"
  "openrouter|z-ai/glm-5.2:free|https://openrouter.ai/api/v1|OPENROUTER_API_KEY"
  "openrouter|minimax/minimax-m3:free|https://openrouter.ai/api/v1|OPENROUTER_API_KEY"
  "openrouter|nvidia/nemotron-3-ultra-550b-a55b:free|https://openrouter.ai/api/v1|OPENROUTER_API_KEY"
  "openrouter|openai/gpt-oss-120b|https://openrouter.ai/api/v1|OPENROUTER_API_KEY"
)

max_attempts="${AI_PROVIDER_MAX_ATTEMPTS:-1}"
delay="${AI_PROVIDER_INITIAL_DELAY:-10}"
log_file="${RUNNER_TEMP:-/tmp}/codex-agent.log"
provider_unavailable=false
tried_provider=false

for candidate in "${candidates[@]}"; do
  IFS='|' read -r provider model base_url env_key <<< "$candidate"
  token="${!env_key:-}"

  if [ -z "$token" ]; then
    echo "Skipping $provider/$model: $env_key is not configured."
    continue
  fi
  tried_provider=true

  cat > "$CODEX_HOME/config.toml" <<EOF
model = "$model"
model_provider = "$provider"
approval_policy = "never"
sandbox_mode = "danger-full-access"

[model_providers.$provider]
name = "$provider"
base_url = "$base_url"
env_key = "$env_key"
EOF

  for attempt in $(seq 1 "$max_attempts"); do
    echo "Starting autonomous Codex cycle with $provider/$model (attempt $attempt/$max_attempts)"
    set +e
    : > "$log_file"
    codex exec --dangerously-bypass-approvals-and-sandbox --skip-git-repo-check "$PROMPT" 2>&1 | tee "$log_file"
    status=${PIPESTATUS[0]}
    set -e

    if [ "$status" -eq 0 ]; then
      echo "AI_PROVIDER_SUCCESS=$provider/$model" > "$PWD/.ai-provider-status"
      exit 0
    fi

    if grep -Eqi '(^|[^0-9])(400|401|403|404|408|409|429|500|502|503|504)([^0-9]|$)|Unauthorized|Forbidden|Not Found|unavailable for free|Too Many Requests|rate limit|rate-limited|temporarily unavailable|capacity|provider.*unavailable|no available provider|Server tool request failed|unexpected argument|tool request.*bad request|HTTP 400|HTTP 401|HTTP 403|status: 400|status: 401|status: 403' "$log_file"; then
      echo "Provider/model availability or compatibility failure for $provider/$model; moving to the next provider." >&2
      provider_unavailable=true
      sleep "$delay"
      break
    fi

    echo "Codex failed for a non-recoverable reason on $provider/$model; refusing to repeat the engineering cycle." >&2
    exit "$status"
  done
done

if [ "$provider_unavailable" = true ] || [ "$tried_provider" = false ]; then
  echo "AI_PROVIDER_UNAVAILABLE=true" > "$PWD/.ai-provider-status"
  echo "No configured AI provider could execute the autonomous engineering cycle." >&2
  echo "Continuing to CI validation so genuine Android/project failures remain visible." >&2
  exit 0
fi

echo "Autonomous agent ended without a successful model execution." >&2
exit 1
