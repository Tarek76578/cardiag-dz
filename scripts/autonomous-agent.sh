#!/usr/bin/env bash
set -euo pipefail

: "${OPENROUTER_API_KEY:?OPENROUTER_API_KEY is required}"
export CODEX_HOME="${CODEX_HOME:-$PWD/.codex}"
mkdir -p "$CODEX_HOME"

cat > "$CODEX_HOME/config.toml" <<'EOF'
model = "nvidia/nemotron-3-ultra-550b-a55b:free"
model_provider = "openrouter"
approval_policy = "never"
sandbox_mode = "danger-full-access"

[model_providers.openrouter]
name = "OpenRouter"
base_url = "https://openrouter.ai/api/v1"
env_key = "OPENROUTER_API_KEY"
EOF

codex exec --dangerously-bypass-approvals-and-sandbox --skip-git-repo-check 'You are the sole autonomous principal engineer, QA engineer, security reviewer, automotive diagnostics reviewer, researcher and release engineer for CarDiag DZ.

Read AGENTS.md and agent-state.md first. Inspect the entire repository before changing anything.

MISSION: continuously improve the real CarDiag DZ product with evidence-based, production-quality engineering. Do not create cosmetic or speculative commits.

AUDIT deeply: Android/Kotlin/Compose architecture; navigation/state/lifecycle; permissions; Bluetooth/OBD/ELM327; VIN/DTC/live data; vehicle catalog and data integrity; Arabic RTL; French localization; accessibility; adaptive layouts; loading/empty/error states; performance; memory/battery; privacy; Supabase/auth/RLS; secrets; dependencies; Gradle; tests; runtime coverage; release configuration; and CI/CD.

RESEARCH: use fresh public web research available in the runner. Prefer official Android/Google documentation and authoritative OBD references, then reputable competitor documentation and current Algerian automotive-market sources. Compare CarDiag with Carista, OBDeleven and Torque. Record source URLs and access dates in market-audit.md. Never fabricate compatibility, prices, DTC meanings, automotive facts, service availability, or test results.

PRIORITIZE findings by severity, correctness, security, user impact and release impact. Fix only verified high-value problems that can be safely fixed in this cycle. Preserve existing valid behavior. Add regression tests for meaningful changes. Never weaken or delete tests.

VALIDATE: run relevant static checks, unit tests, lint, debug/release APK builds and AAB build. Use emulator/runtime validation when infrastructure exists. If runtime validation is unavailable, document the exact blocker rather than claiming success.

SECURITY: never expose or commit credentials; never print secrets; never weaken RLS or permissions; never move secrets into APK/source/resources. Do not delete unrelated functionality or rewrite unrelated history.

DOCUMENT: update agent-state.md with date, scope, evidence, research, findings, changes, validation, blockers and next priorities. Update market-audit.md with research evidence.

GIT: review the complete diff for accidental changes and secrets. Commit only safe verified improvements. Push production-quality changes to main when validation passes. Do not modify the manual trigger workflow from inside this agent. Do not create another workflow or another agent.

CONTINUE the engineering loop for the current milestone instead of stopping merely because one check is green.'
