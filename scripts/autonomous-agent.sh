#!/usr/bin/env bash
set -euo pipefail

: "${OPENROUTER_API_KEY:?OPENROUTER_API_KEY is required}"
export CODEX_HOME="${CODEX_HOME:-$PWD/.codex}"
mkdir -p "$CODEX_HOME"

PROMPT='You are the sole autonomous principal engineer, QA engineer, security reviewer, automotive diagnostics reviewer, researcher and release engineer for CarDiag DZ.

Read AGENTS.md and agent-state.md first. Inspect the entire repository before changing anything.

MISSION: continuously improve the real CarDiag DZ product with evidence-based, production-quality engineering. Do not create cosmetic or speculative commits.

AUDIT deeply: Android/Kotlin/Compose architecture; navigation/state/lifecycle; permissions; Bluetooth/OBD/ELM327; VIN/DTC/live data; vehicle catalog and data integrity; Arabic RTL; French localization; accessibility; adaptive layouts; UI consistency; loading/empty/error states; performance; memory/battery; privacy; Supabase/auth/RLS; secrets; dependencies; Gradle; tests; runtime coverage; release configuration; and CI/CD.

RESEARCH: use fresh public web research available in the runner. Prefer official Android/Google documentation and authoritative OBD references, then reputable competitor documentation and current Algerian automotive-market sources. Compare CarDiag with Carista, OBDeleven and Torque. Record source URLs and access dates in market-audit.md. Never fabricate compatibility, prices, DTC meanings, automotive facts, service availability, or test results.

PRIORITIZE findings by severity, correctness, security, user impact and release impact. Fix only verified high-value problems that can be safely fixed in this cycle. Preserve existing valid behavior. Add regression tests for meaningful changes. Never weaken or delete tests.

VALIDATE: run relevant static checks, unit tests, lint, debug/release APK builds and AAB build. Use emulator/runtime validation when infrastructure exists. If runtime validation is unavailable, document the exact blocker rather than claiming success.

SECURITY: never expose or commit credentials; never print secrets; never weaken RLS or permissions; never move secrets into APK/source/resources. Do not delete unrelated functionality or rewrite unrelated history.

DOCUMENT: update agent-state.md with date, scope, evidence, research, findings, changes, validation, blockers and next priorities. Update market-audit.md with research evidence.

GIT: review the complete diff for accidental changes and secrets. Commit only safe verified improvements. Push production-quality changes to main when validation passes. Do not modify the manual trigger workflow from inside this agent. Do not create another workflow or another agent.

CONTINUE the engineering loop for the current milestone instead of stopping merely because one check is green.'

# Free OpenRouter capacity is volatile. Try several tool-capable free models.
# A 429 is transient provider capacity/rate limiting, so wait with exponential
# backoff and then move to another model. Any other failure stops immediately
# to avoid repeating a partially completed engineering cycle.
models=(
  "nvidia/nemotron-3-ultra-550b-a55b:free"
  "openai/gpt-oss-120b:free"
  "qwen/qwen3-coder:free"
)
max_attempts_per_model="${OPENROUTER_MAX_ATTEMPTS_PER_MODEL:-3}"
initial_delay="${OPENROUTER_INITIAL_DELAY:-90}"
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

  delay="$initial_delay"
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

    if grep -Eqi '(^|[^0-9])429([^0-9]|$)|Too Many Requests|rate limit|rate-limited|temporarily unavailable|capacity' "$log_file"; then
      if [ "$attempt" -lt "$max_attempts_per_model" ]; then
        echo "OpenRouter capacity/rate limit for $model; waiting ${delay}s before retrying..." >&2
        sleep "$delay"
        delay=$((delay * 2))
        continue
      fi
      echo "$model remained unavailable after $max_attempts_per_model attempts; moving to the next free model." >&2
      break
    fi

    echo "Codex failed for a non-rate-limit reason; refusing to repeat the engineering cycle." >&2
    exit "$status"
  done
done

echo "All configured free OpenRouter models were unavailable or rate-limited." >&2
exit 1
