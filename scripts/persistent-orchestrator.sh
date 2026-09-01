#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STATE_FILE="agent-state.md"
TASK_FILE="docs/agent-next-task.md"
REPORT_DIR="${RUNNER_TEMP:-/tmp}/cardiag-orchestrator"
mkdir -p "$REPORT_DIR"

for file in "$STATE_FILE" "$TASK_FILE" AGENTS.md; do
  test -f "$file" || { echo "ORCHESTRATOR_ERROR=missing:$file" >&2; exit 10; }
done

test -x scripts/brain-protocol-v1.sh
./scripts/brain-protocol-v1.sh | tee "$REPORT_DIR/brain-protocol.txt"

printf 'timestamp=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$REPORT_DIR/run.txt"
printf 'task_sha256=%s\n' "$(sha256sum "$TASK_FILE" | awk '{print $1}')" >> "$REPORT_DIR/run.txt"
printf 'state_sha256=%s\n' "$(sha256sum "$STATE_FILE" | awk '{print $1}')" >> "$REPORT_DIR/run.txt"

cat > "$REPORT_DIR/handoff.md" <<EOF
# CarDiag Orchestrator Handoff

- Protocol: Brain Protocol v1
- Mode: external-brain + deterministic-executor
- Request: \`.agent/brain/request.md\`
- Response: \`.agent/brain/response.md\`
- Checkpoint: \`.agent/brain/checkpoint.md\`
- Current task source: \`$TASK_FILE\`
- Persistent state: \`$STATE_FILE\`
- Git ref: \`${GITHUB_SHA:-unknown}\`
- Run: \`${GITHUB_RUN_ID:-local}\`

## Contract
The executor may inspect, test, lint, build, checkpoint and validate. Complex implementation decisions belong to the external AI brain. When no AI provider credential is available, the executor remains healthy and preserves the checkpoint rather than inventing code or pretending the task is complete.
EOF

if [ -z "${OPEN_ROUTER_API_KEY:-}${GROQ_API_KEY:-}${DEEPSEEK_API_KEY:-}" ]; then
  echo "ORCHESTRATOR_MODE=external-brain"
  echo "ORCHESTRATOR_STATUS=WAITING_FOR_BRAIN"
  printf '%s\n' 'WAITING_FOR_BRAIN' > "$REPORT_DIR/status"
  git diff --check
  exit 0
fi

echo "ORCHESTRATOR_MODE=ai-assisted-executor"
echo "ORCHESTRATOR_STATUS=RUNNING"
printf '%s\n' 'AI_ASSISTED_EXECUTION' > "$REPORT_DIR/status"

./scripts/autonomous-agent.sh 2>&1 | tee "$REPORT_DIR/agent.log"
