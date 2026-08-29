#!/usr/bin/env bash
set -euo pipefail

export CODEX_HOME="${CODEX_HOME:-$PWD/.codex}"
mkdir -p "$CODEX_HOME"

MISSION_FILE="$PWD/docs/agent-professional-transformation-mission.md"
REQUIREMENTS_FILE="$PWD/docs/agent-current-user-requirements.md"
USER_PRIORITY_FILE="$PWD/docs/agent-user-priority-requirements.md"
for file in "$MISSION_FILE" "$REQUIREMENTS_FILE" "$USER_PRIORITY_FILE"; do
  if [ ! -f "$file" ]; then
    echo "Missing agent input: $file" >&2
    exit 1
  fi
done

PROMPT="$(cat "$MISSION_FILE")

--- CURRENT USER REQUIREMENTS (MANDATORY ADDENDUM) ---
$(cat "$REQUIREMENTS_FILE")

--- EXPLICIT USER-PRIORITY PRODUCT REQUIREMENTS (MANDATORY) ---
$(cat "$USER_PRIORITY_FILE")"

# Gemini is the primary autonomous-engineering provider. Google documents this
# OpenAI-compatible endpoint and function-calling support. Validate the API
# before invoking Codex so provider failures cannot be reported as success.
GEMINI_MODEL="${GEMINI_MODEL:-gemini-3.7-flash}"
GEMINI_BASE_URL="https://generativelanguage.googleapis.com/v1beta/openai"
if [ -z "${GEMINI_API_KEY:-}" ]; then
  echo "GEMINI_API_KEY is required for the autonomous engineering cycle." >&2
  echo "AI_PROVIDER_UNAVAILABLE=true" > "$PWD/.ai-provider-status"
  exit 1
fi

export GEMINI_MODEL

echo "Validating Gemini API: $GEMINI_MODEL"
validation_payload=$(python3 - <<'PY'
import json, os
print(json.dumps({
  "model": os.environ.get("GEMINI_MODEL", "gemini-3.7-flash"),
  "messages": [{"role": "user", "content": "Reply with exactly GEMINI_READY"}],
  "max_tokens": 16,
}))
PY
)
validation_response=$(curl -fsS --retry 2 --retry-delay 2 \
  -X POST "$GEMINI_BASE_URL/chat/completions" \
  -H "Authorization: Bearer $GEMINI_API_KEY" \
  -H "Content-Type: application/json" \
  -d "$validation_payload") || {
    echo "Gemini API validation failed." >&2
    echo "AI_PROVIDER_UNAVAILABLE=true" > "$PWD/.ai-provider-status"
    exit 1
  }

if ! printf '%s' "$validation_response" | grep -q 'GEMINI_READY'; then
  echo "Gemini API responded, but the expected completion was not received." >&2
  echo "AI_PROVIDER_UNAVAILABLE=true" > "$PWD/.ai-provider-status"
  exit 1
fi

echo "Gemini API validation succeeded: $GEMINI_MODEL"

# Configure Codex to use the same documented Gemini OpenAI-compatible endpoint.
# The API key remains in the environment and is never written to repository files.
cat > "$CODEX_HOME/config.toml" <<EOF
model = "$GEMINI_MODEL"
model_provider = "gemini"
approval_policy = "never"
sandbox_mode = "danger-full-access"

[model_providers.gemini]
name = "gemini"
base_url = "$GEMINI_BASE_URL/"
env_key = "GEMINI_API_KEY"

[projects."/home/runner/work/cardiag-dz/cardiag-dz"]
trust_level = "trusted"
EOF

log_file="${RUNNER_TEMP:-/tmp}/codex-agent.log"
echo "Starting autonomous Codex cycle with gemini/$GEMINI_MODEL"
set +e
: > "$log_file"
codex exec --dangerously-bypass-approvals-and-sandbox --skip-git-repo-check "$PROMPT" 2>&1 | tee "$log_file"
status=${PIPESTATUS[0]}
set -e

if [ "$status" -eq 0 ]; then
  echo "AI_PROVIDER_SUCCESS=gemini/$GEMINI_MODEL" > "$PWD/.ai-provider-status"
  exit 0
fi

echo "Codex autonomous engineering cycle failed after Gemini API validation." >&2
echo "AI_PROVIDER_UNAVAILABLE=true" > "$PWD/.ai-provider-status"
exit "$status"
