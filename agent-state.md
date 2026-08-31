# Autonomous Agent State

The repository uses `.github/workflows/groq-autonomous-agent.yml` as the historical workflow path, now converted to the **CarDiag Autonomous Agent (OpenRouter)**. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Current AI-agent architecture
- Provider: OpenRouter.
- Secret: `OPENROUTER_API_KEY` from GitHub Actions secrets only.
- OpenRouter endpoint: `https://openrouter.ai/api/v1`.
- The previous Groq compatibility proxy is no longer part of the active autonomous workflow.
- Model selection is dynamically probed against OpenRouter's available models, with preferred free coding models first and additional free candidates as fallback.
- The workflow probes `/models` and then validates a candidate through `/responses` before starting Codex.
- The API key is never printed, committed, placed in Android sources, or persisted into artifacts.

## Migration completed
- Converted `.github/workflows/groq-autonomous-agent.yml` from Groq-specific authentication/proxy execution to OpenRouter-native execution.
- Replaced `GROQ_API_KEY` requirement with `OPENROUTER_API_KEY`.
- Added OpenRouter model discovery and Responses API preflight.
- Preserved the autonomous Codex loop, Android validation, diff checks, and verified commit/push gate.
- The former Groq TPM/413 compatibility work remains in repository history but is no longer on the active agent execution path.

## Validation status
- Workflow migration commit: `2f40ff38df81a5ba2fc21ecd414fa3f6dcf8e3bf`.
- A fresh `workflow_dispatch` runtime run is required to prove OpenRouter authentication, model probing, Codex execution, Android validation, and autonomous commit behavior.
- Do not claim the new agent is runtime-green until that workflow run provides evidence.

## Product baseline
The last known product baseline reported:
- Kotlin compilation: clean.
- Unit tests: 141/141 passing across 22 suites.
- Lint (debug): 0 errors, warnings unchanged.
- Debug APK, unsigned release APK, and release AAB build successfully.
- `verify.sh` passes end-to-end.

## Highest-priority backlog for the next cycle
1. Establish a green runtime result for the new OpenRouter autonomous agent.
2. Wire a real, verifiable `NearbySearchProvider` + `HazardsProvider` for the Algerian market.
3. Replace the placeholder `vehicle_silhouette` with generation-aware artwork when a suitable on-device pipeline is available.
4. Add a `LiveMeasurement`-based structured Freeze Frame view.
5. Add OBD hardware validation on a maintained CI runner.
6. Expand the offline DTC catalog with more B/C/U codes and richer repair guidance without inventing facts.
7. Continue RTL accessibility audit (touch targets, contrast, semantic labels, dynamic font scale).
