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

## GPS + interactive map milestone — evidence
- Scope per `docs/agent-next-task.md`: GPS + interactive map only (no background tracking, no location persistence, no new deps). Implemented in `android/app/src/main/java/dz/cardiag/app/core/road/MapDefaults.kt` (41 lines: `MapDefaults.DEFAULT_ZOOM`, `MIN_ZOOM`, `MAX_ZOOM`, `DEFAULT_LATITUDE`, `DEFAULT_LONGITUDE`, `validateZoom`), `core/road/MapProjection.kt` (78 lines, deterministic Web-Mercator-style projection with NaN-safe math, clamped zoom, null/zero/negative-canvas handling), and `ui/components/InteractiveMapView.kt` (263 lines, pure-Compose canvas view: world grid, lat/lon graticule, default-location dot, current-location pin, zoom +/- controls, pan, accessibility/content-description).
- Strings: added `ra_map_title`, `ra_map_subtitle`, `ra_map_default_location`, `ra_map_no_location` to both `res/values/strings.xml` (FR) and `res/values-ar/strings.xml` (AR).
- `RoadAssistantScreen.kt`: imports `dz.cardiag.app.ui.components.InteractiveMapView`; `item { RoadAssistantMapCard(snapshot?.location, arabic) }` inserted at line 202 between controls and the source card; `private fun RoadAssistantMapCard` composable at line 324 wraps `InteractiveMapView`.
- Fixed AAPT2 escape: `ra_map_no_location` (FR) `l'Algérie` -> `l\'Algérie`.
- Tests added under `app/src/test/java/dz/cardiag/app/core/`:
  - `MapDefaultsTest.kt` - 4 cases (null zoom -> `DEFAULT_ZOOM`, NaN zoom -> `DEFAULT_ZOOM`, below-min clamps to `MIN_ZOOM`, above-max clamps to `MAX_ZOOM`).
  - `MapProjectionTest.kt` - 12 cases (center at 0,0; north/east hemispheres; zero / negative canvas falls back to 0; NaN lat/lon yields 0; +/-91 latitude clamped to +/-90; zoom < min clamps but not null; zoom > max clamps but not null; null zoom falls back to `MIN_ZOOM`).
  - Extended `RoadAssistantLocalizationTest.kt` - added the four `ra_map_*` keys to both the FR and AR `required` lists and to the Arabic-script-presence check list.
- Validation (re-run this resumed cycle from `android/`, `--rerun-tasks` where applicable):
  - `./gradlew :app:testDebugUnitTest --rerun-tasks` -> 25 suites, 157 tests, 0 failures, 0 errors, 0 skipped (baseline was 22 suites / 141 tests; +3 suites, +16 tests from `MapDefaultsTest`, `MapProjectionTest`, and the extended `RoadAssistantLocalizationTest`).
  - `./gradlew :app:lintDebug --rerun-tasks` -> BUILD SUCCESSFUL, 0 errors, 269 warnings, 12 information (unchanged from baseline; no new warnings introduced).
  - `./gradlew :app:assembleDebug` -> BUILD SUCCESSFUL, `app-debug.apk` (~22.4 MB) written under `app/build/outputs/apk/debug/`.
- Working tree state (intentionally not committed): `RoadAssistantScreen.kt` (M), `values/strings.xml` (M), `values-ar/strings.xml` (M), `RoadAssistantLocalizationTest.kt` (M), plus untracked `MapDefaults.kt`, `MapProjection.kt`, `InteractiveMapView.kt`, `MapDefaultsTest.kt`, `MapProjectionTest.kt`. The `.codex/*` runtime SQLite/tmp state is preserved untouched.

GPS_MAP_MILESTONE=COMPLETE
