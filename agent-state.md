# Autonomous Agent State

The repository uses `.github/workflows/groq-autonomous-agent.yml` as the historical workflow path, now converted to the **CarDiag Autonomous Agent (OpenRouter)**. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Current AI-agent architecture
- Provider: OpenRouter.
- GitHub Actions secret: `OPEN_ROUTER_API_KEY`.
- OpenRouter endpoint: `https://openrouter.ai/api/v1`.
- The previous Groq compatibility proxy is no longer part of the active autonomous workflow.
- Model discovery reads the `/models` response from a temporary file, avoiding OS argument/environment-size limits.
- Candidate selection is restricted to explicitly approved coding-agent models; arbitrary `:free` models are not accepted merely because they answer HTTP 200.
- The selected model is probed through `/responses` before Codex starts.
- The API key is never printed, committed, placed in Android sources, or persisted into artifacts.

## Runtime findings and fixes
- The first OpenRouter migration failed because the workflow referenced `OPENROUTER_API_KEY`; the repository secret is actually `OPEN_ROUTER_API_KEY`. Fixed.
- The second run reached OpenRouter but failed with `Argument list too long` while passing the large `/models` JSON through an environment variable. Fixed by file-based parsing.
- The next run successfully reached Codex, but dynamic fallback selected `inclusionai/ling-3.0-flash-fin:free`, which did not provide the expected agent tool behavior and eventually hit HTTP 429 after excessive token usage. Fixed by restricting candidate selection to approved coding-agent models and reducing retry/output budgets.

## Validation status
- A new runtime dispatch is required to prove the hardened agent end-to-end.
- Do not claim the new agent is runtime-green until that workflow run provides evidence through Codex, tests, Android builds, and verified commit behavior.

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
