# Autonomous Agent State

The repository uses exactly one GitHub Actions workflow for autonomous engineering: `.github/workflows/cardiag-agent-main.yml`. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The single agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Current blocker: Groq autonomous-agent compatibility / TPM hardening
The latest observed Groq run (`33324832065`) proved the compatibility layer can authenticate, discover/probe `openai/gpt-oss-120b`, remove unsupported Codex fields including `reasoning.summary`, and obtain successful Responses API traffic. The remaining failure was provider-side TPM rejection: an adapted Codex request was estimated locally below the configured ceiling but Groq reported `Requested 8283, Limit 8000`.

### Root cause identified
- The previous estimator used roughly 4 bytes/token, which is too optimistic for Codex/tool serialization and Groq's tokenizer.
- Context compaction only handled list-form input; string-form input could bypass compaction.
- The old list compaction algorithm could reconstruct retained units in the wrong order.
- A 413 retry could fail to materially change the payload and therefore resend essentially the same oversized request.
- Workflow limits were too close to the observed provider ceiling (`5500` input + `1500` output with only `500` margin).

### Fix applied
- `scripts/codex-groq-capture-proxy.py` was hardened in commit `f0f9f932a5daf6989f7765614614a59fabee7978`.
- Token estimation is intentionally conservative at 3 UTF-8 bytes/token rather than 4.
- Default maximum output is now 800 tokens.
- Input compaction supports both list and string input forms.
- Conversation units retain chronological order; tool items remain atomic.
- A preflight budget prevents intentionally sending a locally oversized request.
- Provider-side 413 recovery now materially reduces output and input on every retry, and then falls back to the secondary model instead of repeatedly resending an unchanged payload.
- Adapted requests are captured for post-failure inspection.
- `.github/workflows/groq-autonomous-agent.yml` was hardened in commit `ae05ac65d7bfb134fbf6b2c684f1b896fd82ce54` with `GROQ_MAX_INPUT_TOKENS=4000`, `GROQ_MAX_OUTPUT_TOKENS=800`, `GROQ_TPM_LIMIT=8000`, and `GROQ_TPM_SAFETY_MARGIN=1000`.
- The workflow now runs `python3 -m py_compile /tmp/codex-groq-capture-proxy.py` before starting the adapter.

### Validation status
- The code changes have been committed to `main`.
- The previous failing run was before these changes and must not be treated as validation of the new implementation.
- A new `workflow_dispatch` run is required to establish runtime evidence. Do not claim the autonomous cycle is fixed until that run passes the adapter stage and the downstream Android validation/commit gates.

## Previous product milestone: 2026-08-29d — Road Assistant / GPS, guest-first auth, hardcoded string cleanup
Baseline Android evidence remains the last known green product baseline:
- Kotlin compilation: clean.
- Unit tests: 141/141 passing across 22 suites.
- Lint (debug): 0 errors, warnings unchanged.
- Debug APK, unsigned release APK, and release AAB build successfully.
- `verify.sh` passes end-to-end.

### Non-negotiable rules preserved
- No secrets, signing credentials, API keys or tokens were added to Android sources.
- No automotive facts, DTC meanings, ECU compatibility, sensor values, prices, local services, addresses, vehicle specifications, diagnostic results or test results are fabricated.
- The Road Assistant remains explicitly offline/curated until a verifiable live provider is implemented.
- RLS, permissions, tests and security controls are not weakened to obtain green CI.

## Highest-priority backlog for the next cycle
1. Establish a green runtime result for the hardened Groq autonomous-agent adapter.
2. Wire a real, verifiable `NearbySearchProvider` + `HazardsProvider` for the Algerian market.
3. Replace the placeholder `vehicle_silhouette` with generation-aware artwork when a suitable on-device pipeline is available.
4. Add a `LiveMeasurement`-based structured Freeze Frame view.
5. Add OBD hardware validation on a maintained CI runner.
6. Expand the offline DTC catalog with more B/C/U codes and richer repair guidance without inventing facts.
7. Continue RTL accessibility audit (touch targets, contrast, semantic labels, dynamic font scale).
