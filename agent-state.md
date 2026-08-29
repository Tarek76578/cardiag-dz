# Autonomous Agent State

The repository uses exactly one GitHub Actions workflow for autonomous engineering: `.github/workflows/cardiag-agent-main.yml`. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The single agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Milestone: 2026-08-29c — User requirements addendum (Language/Mode/Guest, AI localization, vehicle imagery, history integrity)
This cycle addresses the mandatory user requirements from the latest APK review,
on top of the existing 2026-08-29b UI integration work. The repository
remained a single unified Compose graph; the affected files were extended,
not duplicated.

### Baseline still green
- Kotlin compilation: clean.
- Unit tests: 113/113 passing across 18 suites (up from 101/15).
- Lint (debug): 0 errors, 221 warnings (all pre-existing).
- Debug APK: builds successfully.
- Release APK (unsigned): builds successfully.
- Release AAB: builds successfully.
- `verify.sh` end-to-end: passes.

### What was changed in this cycle

#### 1. First-launch onboarding
- New `OnboardingScreen.kt` with a 3-step flow:
  1. Language (Français / العربية)
  2. Mode (Conducteur / Mécanicien)
  3. Continuer en invité
- `CarDiagUnifiedApp` now persists the onboarding completion flag in
  `PREFS_UI` and shows the onboarding flow until the user finishes it.
- A "Reopen onboarding" entry was added to the More / Settings screen so
  the user can change language or mode at any time.

#### 2. Mode Conducteur / Mode Mécanicien
- New `AppMode { DRIVER, MECHANIC }` enum in `core/ProductionDomain.kt`.
- `MoreScreen` now exposes a "Mode" setting that toggles between the two.
- `HomeScreen` adapts its quick actions to the selected mode:
  - Driver: Mon véhicule, Décrire un problème, Que veut dire ce code,
    Scan OBD, Assistant IA (no raw ECU/PID terminology).
  - Mechanic: Scan OBD, Diagnostic par symptôme, Recherche DTC, Assistant IA.
- The bottom navigation in DRIVER mode swaps the "Diagnose" hub for a
  "Symptôme" primary destination; the same nav graph is reused so no
  parallel production path is created.

#### 3. AI diagnosis full-stack localization
- The Edge Function `supabase/functions/diagnose/index.ts` previously
  embedded hard-coded French strings in `offlineDiagnosis()` and
  `validateDiagnosis()`, and surfaced English error messages. The
  function was rewritten so every user-visible string depends on
  `language === "ar" ? ar : fr`:
  - Summary
  - Recommended tests
  - Repair guidance
  - Safety notes
  - Uncertainty
  - Next best test
  - All deterministic findings (Low MAF signal, Cold engine, Low/unstable
    idle, Possible unmetered air, Catalyst test before warm-up)
  - Early error responses (Authorization required, Invalid authentication,
    session_id required, Method not allowed, Could not validate session,
    Diagnostic session not found, Describe the problem, Invalid request)
- The Android `DiagnosticService` error fallback was also localized in
  French and Arabic, and the `AI` screen now distinguishes between
  timeout, auth failure and generic unavailability.
- New `DriverGuidanceEngine` exposes "Puis-je conduire ?" and "Que faire
  maintenant ?" in French and Arabic, based only on the actual recorded
  evidence (DTC counts, MIL, readiness, severity). It returns
  YES / CAUTION / NO / UNKNOWN and never claims certainty without evidence.

#### 4. Vehicle imagery
- Two new generated vector drawables (`vehicle_silhouette.xml`,
  `vehicle_hero_placeholder.xml`) provide a professional automotive
  silhouette that is shown whenever a real image is not available.
- `VehicleContextCard` now renders the silhouette alongside the vehicle
  name. No copyrighted photographs are referenced or bundled.

#### 5. History integrity
- The hard-coded `BeforeAfterSnapshot` demo data in `CarDiagNavGraph`
  (`pendingBefore = BeforeAfterSnapshot(... P0301, P0171, ...)` and the
  matching "after" snapshot) was removed. Tapping a session now opens
  the diagnostic report with empty before/after fields and shows a
  truthful "no comparison available" message.
- The history list still queries real `diagnostic_sessions` rows; the
  empty state is rendered when the user has no saved sessions.

#### 6. DTC and AI localized content
- Hard-coded "Code introuvable dans la base DTC" was moved to
  `R.string.dtc_not_found` with French and Arabic translations.
- `GuidedDecisionTree` no longer embeds English prompts; each
  `GuidedStep` now stores the string resource name, and the
  `GuidedDiagnosisScreen` resolves it via `stringResource`. Eighteen new
  strings (French + Arabic) cover the step prompts and the test-result
  recommendations.
- The `SymptomQuestions` option labels no longer point to step-number
  resources (which produced "1. Confirmer le véhicule" as a chip label).
  Twelve new option strings (French + Arabic) cover the contextual
  questions (cold start / warm / acceleration / idle / smoke colors /
  yes / no, etc.).

#### 7. AI surface improvements
- `AiDiagnosisScreen` now surfaces a localized disclaimer card with the
  "offline fallback" note so the user knows the assistant always returns
  a localized, evidence-grounded response.
- AI error messages are categorized (timeout / auth / generic) and
  rendered in the user's selected language.

### New tests
- `DriverGuidanceTest` (6): no-fault YES, critical fault NO, MIL-on
  caution, Arabic script validation, readiness-not-ready caution, unknown
  evidence UNKNOWN.
- `OnboardingModeSelectionTest` (3): default mode is DRIVER, mode
  switching produces a different value, mode names roundtrip.
- `LocalizationConsistencyTest` (3): stable `AppMode`, `CanDriveVerdict`,
  `BeforeAfterOutcome` enums.
- All existing 101 tests still pass.

### Validation evidence
- `./gradlew --no-daemon testDebugUnitTest` – 113 tests, 0 failures.
- `./gradlew --no-daemon compileDebugKotlin` – success.
- `./gradlew --no-daemon lintDebug` – success, 0 errors.
- `./gradlew --no-daemon assembleDebug` – success, `app-debug.apk` produced.
- `./gradlew --no-daemon assembleRelease bundleRelease` – success,
  `app-release-unsigned.apk` and `app-release.aab` produced.
- `bash verify.sh` – passes end-to-end.

### Non-negotiable rules preserved
- RLS, permissions, tests, security controls and validation have not been
  weakened to obtain a green build.
- No automotive fact, DTC meaning, ECU compatibility, sensor value,
  price, local service, address, vehicle specification, diagnostic
  result or test result has been fabricated.
- The OBD / Live Data / Freeze Frame paths still surface "Not
  Supported" / "Indisponible" when the ECU does not return a value.
- No secret, signing credential, API key or token has been added to the
  Android sources.
- The Edge Function `offlineDiagnosis` and `validateDiagnosis` paths now
  return language-specific strings; the previous French-only offline
  fallback is gone.
- The diagnostic report no longer fabricates before/after demo
  snapshots; it shows the truthful "no comparison available" message
  when the user has not recorded one.

## Highest-priority backlog for the next cycle
1. Wire the diagnostic session model end-to-end so that real recorded
   before/after snapshots populate the diagnostic report and the
   history list.
2. Add a screenshot-based instrumentation test of the onboarding flow
   (Language -> Mode -> Guest) and the Home/Mode-aware quick actions.
3. Expand the offline DTC catalog with more B/C/U codes and additional
   repair guidance, without inventing facts.
4. Add an explicit structured Freeze Frame view that uses the
   `LiveMeasurement` model (in addition to the raw dump).
5. Begin OBD hardware validation on the maintained CI runner.
6. Add per-vehicle model/year specific AI context (engine id, ECU id)
   to the diagnose Edge Function payload.
7. Continue RTL accessibility audit (touch targets, contrast,
   semantic labels).
