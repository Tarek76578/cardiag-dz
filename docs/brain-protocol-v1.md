# CarDiag Brain Protocol v1

## Purpose

Brain Protocol v1 separates **reasoning** from **execution**. The repository is the durable control plane; an external brain (including ChatGPT) may provide decisions without being a continuously running API process.

## Contract

The protocol lives under `.agent/brain/`:

- `request.md` — executor's current request for reasoning.
- `response.md` — optional decision supplied by the external brain.
- `checkpoint.md` — durable execution handoff.
- `status` — one of `READY`, `WAITING_FOR_BRAIN`, `EXECUTING`, `BLOCKED`, `COMPLETE`.

### Request

The executor writes a bounded request containing:

1. task identity from `docs/agent-next-task.md`;
2. relevant state from `agent-state.md`;
3. current git status/diff summary;
4. validation or blocker output;
5. an explicit question for the brain.

Never put API keys, credentials, or private secrets in the request.

### Response

A brain response must contain:

- `DECISION:` — the decision or next action;
- `SCOPE:` — files/areas allowed to change;
- `VALIDATION:` — tests/checks required;
- `STOP_IF:` — conditions that must prevent speculative changes.

The response is advisory input. The executor remains responsible for validating it and must reject unsafe, unrelated, or unverifiable instructions.

## Lifecycle

```text
READY -> WAITING_FOR_BRAIN -> EXECUTING -> CHECKPOINT -> WAITING_FOR_BRAIN
                                      \-> BLOCKED
                                      \-> COMPLETE
```

If no brain response is available, the executor must not invent a reasoning result merely to keep moving. It may perform deterministic validation/checkpoint work and then wait.

## Security and durability

- `.agent/brain/response.md` must never contain secrets.
- Brain files are ephemeral project state, not a location database or user-location store.
- No background location tracking is introduced by this protocol.
- Every execution cycle must leave a concise checkpoint so a later brain session can resume without reconstructing the entire conversation.
