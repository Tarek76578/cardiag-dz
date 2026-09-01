#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BRAIN_DIR="$ROOT/.agent/brain"
mkdir -p "$BRAIN_DIR"
chmod 700 "$BRAIN_DIR"

write_request() {
  local question="${1:-Provide the next safe implementation decision.}"
  {
    printf '%s\n' '# CarDiag Brain Request v1'
    printf '\nTASK\n====\n'
    sed -n '1,220p' "$ROOT/docs/agent-next-task.md"
    printf '\nSTATE\n=====\n'
    sed -n '1,220p' "$ROOT/agent-state.md"
    printf '\nGIT STATUS\n==========\n'
    git -C "$ROOT" status --short
    printf '\nGIT DIFF STAT\n=============\n'
    git -C "$ROOT" diff --stat
    printf '\nQUESTION\n========\n%s\n' "$question"
  } > "$BRAIN_DIR/request.md"
  printf 'WAITING_FOR_BRAIN\n' > "$BRAIN_DIR/status"
}

consume_response() {
  local response="$BRAIN_DIR/response.md"
  test -s "$response" || return 1
  grep -q '^DECISION:' "$response" || return 2
  grep -q '^SCOPE:' "$response" || return 2
  grep -q '^VALIDATION:' "$response" || return 2
  grep -q '^STOP_IF:' "$response" || return 2
  return 0
}

checkpoint() {
  local reason="${1:-checkpoint}"
  {
    printf '%s\n' '# CarDiag Brain Checkpoint v1'
    printf '\nTIME\n====\n%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '\nREASON\n======\n%s\n' "$reason"
    printf '\nTASK\n====\n'
    sed -n '1,160p' "$ROOT/docs/agent-next-task.md"
    printf '\nSTATE\n=====\n'
    sed -n '1,200p' "$ROOT/agent-state.md"
    printf '\nGIT STATUS\n==========\n'
    git -C "$ROOT" status --short
    printf '\nDIFF STAT\n=========\n'
    git -C "$ROOT" diff --stat
  } > "$BRAIN_DIR/checkpoint.md"
}

case "${1:-request}" in
  request) write_request "${2:-Provide the next safe implementation decision.}" ;;
  consume) consume_response ;;
  checkpoint) checkpoint "${2:-checkpoint}" ;;
  *) echo "usage: $0 {request|consume|checkpoint} [text]" >&2; exit 2 ;;
esac
