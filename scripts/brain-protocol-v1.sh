#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STATE_FILE="agent-state.md"
TASK_FILE="docs/agent-next-task.md"
BRAIN_DIR=".agent/brain"
REQUEST_FILE="$BRAIN_DIR/request.md"
RESPONSE_FILE="$BRAIN_DIR/response.md"
CHECKPOINT_FILE="$BRAIN_DIR/checkpoint.md"

mkdir -p "$BRAIN_DIR"
test -f "$STATE_FILE"
test -f "$TASK_FILE"

now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
run_id="${GITHUB_RUN_ID:-local}"
sha="${GITHUB_SHA:-unknown}"

task_sha="$(sha256sum "$TASK_FILE" | awk '{print $1}')"
state_sha="$(sha256sum "$STATE_FILE" | awk '{print $1}')"

cat > "$REQUEST_FILE" <<EOF
# Brain Protocol v1 — Request

protocol: 1
created_at: $now
run_id: $run_id
git_sha: $sha
task_sha256: $task_sha
state_sha256: $state_sha

## Mission
Read and follow the current task in \`$TASK_FILE\`. Preserve existing intentional work. Do not claim completion without validation.

## Current task
$(cat "$TASK_FILE")

## Persistent state
$(cat "$STATE_FILE")

## Required response contract
The external brain may return a plan, exact implementation instructions, a patch, or a blocker. It must never fabricate test/build results. The executor must validate every claimed change before marking completion.
EOF

if [ ! -f "$RESPONSE_FILE" ]; then
  cat > "$RESPONSE_FILE" <<EOF
# Brain Protocol v1 — Response

status: WAITING_FOR_BRAIN
created_at: $now
run_id: $run_id

No external brain response has been supplied for this checkpoint. The executor must not invent implementation decisions.
EOF
fi

cat > "$CHECKPOINT_FILE" <<EOF
# Brain Protocol v1 — Checkpoint

updated_at: $now
run_id: $run_id
git_sha: $sha
task_sha256: $task_sha
state_sha256: $state_sha

The repository contains the durable request/response contract for the external brain. This checkpoint is safe to carry across runner lifetimes.
EOF

echo "BRAIN_PROTOCOL_VERSION=1"
echo "BRAIN_REQUEST=$REQUEST_FILE"
echo "BRAIN_RESPONSE=$RESPONSE_FILE"
echo "BRAIN_CHECKPOINT=$CHECKPOINT_FILE"
