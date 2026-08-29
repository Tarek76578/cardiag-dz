# Autonomous Agent State

The repository now uses exactly one GitHub Actions workflow for autonomous engineering: `.github/workflows/cardiag-agent-main.yml`. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The single agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Milestone: 2026-08-29b — UI integration of canonical data layers
This cycle integrated the existing canonical data layers (SymptomCatalog,
DtcKnowledgeCatalog, BeforeAfterComparison) into the production UI so users
can actually exercise them. It also expanded adaptive layout coverage,
added regression tests and kept the build green.

### Restored baseline (still green)
- Kotlin compilation: clean.
- Unit tests: 101/101 passing across 15 suites (up from 83/12).
- Lint (debug): zero errors.
- Debug APK: builds successfully.
- Release APK (unsigned): builds successfully.
- Release AAB: builds successfully.
- `verify.sh` end-to-end: passes.

### UI integration work
- New file `SymptomScreens.kt` – the symptom-diagnosis screen now consumes
  `SymptomCatalog` and `SymptomQuestions` directly. Users can:
  - pick a category chip,
  - multi-select specific symptoms for that category (chips toggle),
  - answer deterministic contextual questions whose presence depends on
    the selected symptom IDs,
  - send the structured selection to the AI endpoint.
  The old duplicate `SymptomCategory` / `SymptomSeverity` enums and the
  bespoke chip row were removed from `CarDiagDetailScreens.kt`. The old
  free-form complaint/when/engine state text fields are still available
  for free-text symptoms (handled by the AI as a fallback).
- New file `DtcBrowseScreens.kt` – a real "Browse" surface backed by the
  offline `DtcKnowledgeCatalog`. The screen supports:
  - free-text search (code or title),
  - family filter (P / B / C / U),
  - severity filter (critical / warning / info),
  - a two-pane list | detail layout on Expanded (>=840 dp width),
  - a single scrolling list on Compact / Medium,
  - a "Choose this code" CTA that navigates to the full remote DTC detail.
  The browse logic was extracted to a pure-Kotlin `DtcBrowseFilter` helper
  with a dedicated unit test. The DTC detail screen now exposes a
  "Browse" OutlinedButton that opens the new screen.
- New file `BeforeAfterScreens.kt` – a production
  `BeforeAfterComparisonCard` widget and a refactored
  `DiagnosticReportScreen`. The History screen now seeds deterministic
  offline before/after snapshots and pushes the user to the report
  screen, where the comparison outcome (IMPROVED / SAME / REGRESSED /
  INSUFFICIENT) is rendered with full French and Arabic copy. The
  existing per-side counts and DTC lists are displayed without
  fabrication.
- Adaptive layout: the Home screen now uses `rememberCarDiagWindowSize`
  and renders a two-column action grid on Expanded while staying
  single-column on Compact / Medium. The DTC browse screen renders a
  two-pane list | detail on Expanded.
- `ArrowForward` is now the AutoMirrored variant so the before/after
  arrow flips correctly in Arabic RTL.
- The `CarDiagRoute` enum gained a stable `DTC_BROWSE` value; navigation
  through the existing graph still works.
- 31 new FR/AR string resources cover the new copy: symptom selection
  state, contextual questions, browse filters, browse empty state,
  before/after outcome labels, before/after side metrics, adaptive
  layout panes.

### Test coverage
- New: `SymptomSelectionTest` (7), `BeforeAfterUiMappingTest` (5),
  `DtcBrowseFilterTest` (6).
- Existing 83-test suite remains green: BeforeAfterComparisonTest,
  VehicleHealthEngineTest, ProductionDomainTest, ScanStateMachineTest,
  AdaptiveLayoutsTest, SymptomCatalogTest, DtcKnowledgeCatalogTest,
  VinValidatorTest, DiagnosticCorrelationTest, VinDecoderTest,
  DiagnosticEngineTest, ObdParserTest.

## Highest-priority backlog for the next cycle
1. Replace the placeholder before/after sample snapshots with a real
   diagnostic session model that persists the user's evidence
   (dtcs/readiness/MIL/measurements) and is loaded from Supabase.
2. Continue Arabic accessibility contentDescription audit; add minimum
   touch-target tests using Compose UI test or a static helper.
3. Wire `DtcBrowseScreens` and the report flow into the Diagnose hub
   shortcuts so users do not have to navigate to Home first.
4. Emulator / instrumentation test pass for navigation, garage selection
   and home rendering.
5. Begin OBD hardware validation script on the maintained CI runner.
6. Enrich the canonical DTC catalog (more codes, B/C/U coverage,
   repair guidance) without fabricating facts.
7. Add an explicit "Freeze Frame" structured view alongside the raw
   dump, leveraging the same measurement model used for Live Data.

## Validation evidence
- `./gradlew --no-daemon testDebugUnitTest` – 101 tests, 0 failures.
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
- The OBD / Live Data / Freeze Frame paths still surface "Not
  Supported" / "Indisponible" when the ECU does not return a value.
- No secret, signing credential, API key or token has been added to the
  Android sources.
- The before/after sample snapshots are explicitly seeded by the
  navigation graph and labelled as such; the comparison engine itself
  never invents DTCs or readiness values.
