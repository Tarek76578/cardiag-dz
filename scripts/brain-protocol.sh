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
  printf 'BRAIN_RESPONSE_VALID\n' > "$BRAIN_DIR/status"
  return 0
}

install_response() {
  local source="${2:-}"
  if [ -n "$source" ] && [ -f "$source" ]; then
    cp "$source" "$BRAIN_DIR/response.md"
  elif [ -n "${BRAIN_RESPONSE_FILE:-}" ] && [ -f "$BRAIN_RESPONSE_FILE" ]; then
    cp "$BRAIN_RESPONSE_FILE" "$BRAIN_DIR/response.md"
  elif [ -n "${BRAIN_RESPONSE:-}" ]; then
    printf '%s\n' "$BRAIN_RESPONSE" > "$BRAIN_DIR/response.md"
  else
    echo "No Brain response supplied" >&2
    return 3
  fi
  consume_response
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
    if [ -f "$BRAIN_DIR/response.md" ]; then
      printf '\nBRAIN RESPONSE\n==============\n'
      sed -n '1,220p' "$BRAIN_DIR/response.md"
    fi
  } > "$BRAIN_DIR/checkpoint.md"
}

case "${1:-request}" in
  request) write_request "${2:-Provide the next safe implementation decision.}" ;;
  consume) consume_response ;;
  install-response) install_response "${1:-}" "${2:-}" ;;
  checkpoint) checkpoint "${2:-checkpoint}" ;;
  *) echo "usage: $0 {request|consume|install-response|checkpoint} [text|file]" >&2; exit 2 ;;
esac
