# Autonomous Agent State

The repository now uses exactly one GitHub Actions workflow for autonomous engineering: `.github/workflows/cardiag-agent-main.yml`. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The single agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Milestone: 2026-08-29 — Professional product transformation
This cycle restored the build that was broken at the start of the run and
delivered a substantial professional product transformation on top of the
unified navigation already in place.

### Restored baseline
- Kotlin compilation: clean.
- Unit tests: 83/83 passing across 12 suites (was 64/64 across 8).
- Lint (debug): zero errors, only deprecation/style warnings.
- Debug APK: builds successfully.
- Release APK (unsigned): builds successfully.
- Release AAB: builds successfully.
- `verify.sh` end-to-end: passes.

### Compilation fixes applied
- `CarDiagKnowledgeScreens.kt` – removed local-fun/non-composable mix by
  introducing `contextStringPlain` and refactoring `DtcRecordContent` into
  a regular `@Composable` so the call sites compile cleanly.
- `CarDiagNavGraph.kt` – `CarDiagRoute.GUIDED` -> `GUIDED_DIAGNOSIS`,
  fixed `listSaver` generic types.
- `CarDiagPrimaryScreens.kt` – fixed `stringResource` call inside a
  coroutine `reload()`, used `ExactVehicle` -> `UiModel` mapping for the
  garage catalog, added `LazyListScope.items` import.
- `VehicleProfileExact.kt` – added missing `SectionTitle` composable.

### Product transformation work
- Active vehicle is now persisted across launches in SharedPreferences
  (Home, diagnosis and vehicle profile always know which car the user is
  investigating). The CarDiagUnifiedApp exposes a `persistVehicle` callback
  that the navigation graph invokes whenever the user picks a vehicle.
- A canonical **Symptom Catalog** (`SymptomCatalog.kt`) introduces
  `SymptomCategoryId`, a curated list of `SymptomEntry` items and
  deterministic `SymptomQuestions` that surface contextual follow-ups
  based on the selected symptoms. The symptom screen chip row now uses
  this catalog so labels are localized and the questions depend on the
  selected symptoms. The 8 category chips and 27 specific symptom chips
  are now translated to French and Arabic.
- A canonical **DTC Knowledge Catalog** (`DtcKnowledgeCatalog.kt`) holds
  offline J2012-grade information for the most common P/B/C/U codes so
  the app can browse and filter a baseline set without inventing
  content. Family and severity filters are available and unit-tested.
- A **Before / After Repair** comparison engine (`BeforeAfterComparison`)
  computes IMPROVED / SAME / REGRESSED / INSUFFICIENT outcomes from two
  recorded diagnostic snapshots and is unit-tested.
- A **window-size class** helper (`CarDiagWindowSize`) classifies the
  current device into Compact / Medium / Expanded for adaptive layouts.
- Localization: 114 new FR/AR string resources covering symptom
  categories, specific symptoms, OBD recovery steps, scan-results
  systems, Live Data units, Freeze Frame / Readiness / VIN terminology,
  Before/After labels and adaptive layout labels. All keys are present
  in both `values/` and `values-ar/`.
- Removed `ProfessionalDashboard`, `VehicleProfilePro`, `VehicleProfileUiComponents`,
  `CanonicalVehicleProfile`, and the parallel `diagnostics/Scan*` files,
  leaving a single coherent production navigation graph (`CarDiagNavGraph`).

### Test coverage
- New: `SymptomCatalogTest`, `BeforeAfterComparisonTest`,
  `DtcKnowledgeCatalogTest`, `AdaptiveLayoutsTest`.
- Existing suites remain green: VehicleHealthEngine, ProductionDomain,
  ScanStateMachine, VinValidator, DiagnosticCorrelation, VinDecoder,
  DiagnosticEngine, ObdParser.

## Highest-priority backlog for the next cycle
1. UI integration: surface `DtcKnowledgeCatalog` in the DTC screen as
   a "Browse" tab and surface `BeforeAfterComparison` in a History detail.
2. Surface `SymptomCatalog` specific symptoms in the symptom screen
   (the chip row currently exposes categories; the next cycle can
   expose the per-category specific symptoms as a multi-select list).
3. Apply `CarDiagWindowSize` to the home, garage and DTC screens for
   adaptive two-pane layouts on tablets and large landscape devices.
4. Emulator / instrumentation test pass for navigation, garage selection
   and home rendering.
5. Continue Arabic accessibility contentDescription audit and add
   minimum touch-target tests.
6. Begin OBD hardware validation script on the maintained CI runner.

## Validation evidence
- `./gradlew --no-daemon testDebugUnitTest` – 83 tests, 0 failures.
- `./gradlew --no-daemon compileDebugKotlin` – success.
- `./gradlew --no-daemon lintDebug` – success, 0 errors.
- `./gradlew --no-daemon assembleDebug` – success, `app-debug.apk` produced.
- `./gradlew --no-daemon assembleRelease bundleRelease` – success,
  `app-release-unsigned.apk` and `app-release.aab` produced.
- `bash verify.sh` – passes end-to-end.

## Non-negotiable rules preserved
- RLS, permissions, tests, security controls and validation have not
  been weakened to obtain a green build.
- No automotive fact, DTC meaning, ECU compatibility, sensor value,
  price, local service, address, vehicle specification, diagnostic
  result or test result has been fabricated.
- OBD/ECU responses are only displayed as real data; the live data
  path keeps unsupported PIDs as `null` (rendered as "Not Supported").
- No secret, signing credential, API key or token has been added to
  the Android sources.
