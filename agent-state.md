# Autonomous Agent State

The repository uses `.github/workflows/groq-autonomous-agent.yml` as the historical workflow path, now converted to the **CarDiag Autonomous Agent (OpenRouter)**. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Current AI-agent architecture
- Provider: CarDiag local gateway backed by OpenRouter free-model discovery.
- GitHub Actions secret: `OPEN_ROUTER_API_KEY`.
- OpenRouter endpoint: `https://openrouter.ai/api/v1`.
- The active autonomous script discovers untried OpenRouter `:free` models dynamically from `/models` instead of pinning a single free model.
- Candidate selection requires zero prompt/completion pricing, tool support, and at least a 32K context window.
- Each model is used for one Codex continuation cycle. Existing uncommitted working-tree changes are intentionally preserved between cycles.
- Provider-limit failures (429/503/context/token-limit signatures) trigger the next untried free model rather than terminating the milestone.
- `agent-state.md` is the persistent handoff/checkpoint. A cycle must only write `GPS_MAP_MILESTONE=COMPLETE` after it genuinely believes the GPS/map milestone is complete.
- The wrapper independently verifies that completion marker and then runs `testDebugUnitTest`, `lintDebug`, and `assembleDebug`; only a passing completion gate allows the agent job to succeed.
- API keys are never printed, committed, placed in Android sources, or persisted into artifacts.

## Runtime findings and fixes
- The first OpenRouter migration failed because the workflow referenced `OPENROUTER_API_KEY`; the repository secret is actually `OPEN_ROUTER_API_KEY`. Fixed.
- The second run reached OpenRouter but failed with `Argument list too long` while passing the large `/models` JSON through an environment variable. Fixed by file-based parsing.
- A later run reached Codex but selected a free model that passed a shallow probe and then hit provider limits. Candidate selection was hardened and free-model discovery was made dynamic.
- The latest agent hardening adds continuation cycles: on provider/token/context exhaustion, preserve the current working tree and `agent-state.md`, select another untried free model, and resume the same GPS/map task.
- Completion is now independently validated; a Codex exit code of zero is not sufficient by itself.

## Validation status
- A new runtime dispatch is required to prove the continuation-cycle and completion-gate behavior end-to-end.
- Do not claim the new agent is runtime-green until that workflow run provides evidence through Codex, tests, Android lint/build, and verified commit behavior.

## Product baseline
- Last known Android baseline: 141/141 unit tests passing across 22 suites.
- Last known lint: 0 errors, warnings unchanged.
- Last known Debug APK, unsigned Release APK, and Release AAB builds: successful.
- `verify.sh` last reported passing end-to-end.

## Highest-priority backlog for the next cycle
1. Establish a green runtime result for the hardened OpenRouter autonomous agent.
2. Wire a real, verifiable `NearbySearchProvider` + `HazardsProvider` for the Algerian market.
3. Replace the placeholder `vehicle_silhouette` with generation-aware artwork when a suitable on-device pipeline is available.
4. Add a `LiveMeasurement`-based structured Freeze Frame view.
5. Add OBD hardware validation on a maintained CI runner.
6. Expand the offline DTC catalog with more B/C/U codes and richer repair guidance without inventing facts.
7. Continue RTL accessibility audit (touch targets, contrast, semantic labels, dynamic font scale).
