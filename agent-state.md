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
- The autonomous script discovers untried OpenRouter `:free` models dynamically from `/models` instead of pinning a single free model.
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
- Continuation cycles preserve the current working tree and `agent-state.md`, select another untried free model, and resume the same GPS/map task after provider exhaustion.
- Completion is independently validated; a Codex exit code of zero is not sufficient by itself.
- The completion marker is now forcibly removed at the start of every autonomous run. This prevents a stale `GPS_MAP_MILESTONE=COMPLETE` from short-circuiting the agent before it verifies the actual GPS implementation.

## Validation status
- The previous run proved the Android project builds successfully, but did not prove that the real-device GPS path is implemented.
- A fresh runtime dispatch is required to let Codex inspect and implement/verify the real GPS + interactive map milestone.

## GPS + interactive map milestone — prior implementation evidence requiring verification
- Existing changes include `MapDefaults.kt`, `MapProjection.kt`, `InteractiveMapView.kt`, localization keys, `RoadAssistantScreen.kt`, and focused map tests.
- The prior implementation evidence described a pure-Compose interactive map and a location snapshot UI, but this is not accepted as completion evidence because a map projection/default-location UI alone does not prove acquisition of a real Android device location.
- The next autonomous cycle must inspect the actual permission flow and Android location-provider path, connect it to the map, add/adjust regression tests, and only then restore `GPS_MAP_MILESTONE=COMPLETE` if genuinely satisfied.

## Highest-priority backlog for the next cycle
1. Complete and verify the real GPS + interactive map milestone: runtime permission, real Android location acquisition, actual current-position marker, lifecycle/error handling, focused tests, lint and debug build.
2. Establish a green runtime result for the hardened OpenRouter autonomous agent.
3. Wire a real, verifiable `NearbySearchProvider` + `HazardsProvider` for the Algerian market.
4. Replace the placeholder `vehicle_silhouette` with generation-aware artwork when a suitable on-device pipeline is available.
5. Add a `LiveMeasurement`-based structured Freeze Frame view.
6. Add OBD hardware validation on a maintained CI runner.
7. Expand the offline DTC catalog with more B/C/U codes and richer repair guidance without inventing facts.
8. Continue RTL accessibility audit (touch targets, contrast, semantic labels, dynamic font scale).

GPS_MAP_MILESTONE=IN_PROGRESS
