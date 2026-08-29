# Autonomous Agent State

The repository uses exactly one GitHub Actions workflow for autonomous engineering: `.github/workflows/cardiag-agent-main.yml`. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The single agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Milestone: 2026-08-29d — Road Assistant / GPS, guest-first auth, hardcoded string cleanup
This cycle extends the previous milestones with the new Road Assistant
(GPS) feature, a guest-first authentication screen, and a thorough pass
at removing hardcoded French/Arabic `if (arabic) "..." else "..."`
conditionals from the production UI.

### Baseline still green
- Kotlin compilation: clean.
- Unit tests: 141/141 passing across 22 suites (up from 113/18).
- Lint (debug): 0 errors, warnings unchanged.
- Debug APK: builds successfully.
- Release APK (unsigned): builds successfully.
- Release AAB: builds successfully.
- `verify.sh` end-to-end: passes.

### What was changed in this cycle

#### 1. Road Assistant / GPS (new)
- New `core/road/` package with a provider-agnostic architecture:
  - `RoadAssistantModels.kt` — coarse location, `NearbyService`,
    `RoadHazard`, `ServiceCategory` and `HazardKind` enums,
    `NearbyResult`/`HazardsResult` sealed results.
  - `RoadAssistantProviders.kt` — `LocationProvider`,
    `NearbySearchProvider`, `HazardsProvider` interfaces plus
    `RoadAssistantSnapshot` data class.
  - `OfflineRoadDataProvider.kt` — curated, generic Arabic + French
    category descriptions and search-query templates (ميكانيكي,
    ميكانيكي سيارات, كهربائي, garage, dépannage, قطع الغيار…). No
    fabricated business, address, phone, rating or distance.
  - `RoadAssistantService.kt` — aggregates location + nearby + hazards
    into a single snapshot; default offline implementation is honest
    about being non-live.
  - `AndroidLocationProvider.kt` — Android `LocationManager`-based
    implementation with permission guard and timeout, no persistence.
  - `RoadAssistantContext.kt` — runtime permission helpers.
- New `RoadAssistantScreen.kt` with:
  - Location permission request card.
  - Per-category filter chips with multi-select.
  - Radius slider (1–20 km).
  - Multilingual search input that understands Arabic and French terms.
  - Honest `Offline catalog` source disclosure.
  - "Open in Maps" + "Search externally" actions per category.
  - Generic hazard status (no fake live data).
  - Emergency numbers (police 17, fire 14, ambulance 115, protection
    civile 1055) with `tel:` dial action.
- New `CarDiagRoute.ROAD_ASSISTANT` wired into the navigation graph.
  Reachable from Home (driver mode quick action) and from the More
  screen (both modes).
- `AndroidManifest.xml` now declares `ACCESS_COARSE_LOCATION` and
  `ACCESS_FINE_LOCATION`. No background location, no
  `ACCESS_BACKGROUND_LOCATION`.

#### 2. Guest-first Auth
- The old `AuthScreen.kt` (with hardcoded French/Arabic conditionals)
  was removed.
- New `AuthScreens.kt` is wired into the navigation graph via
  `CarDiagRoute.AUTH` and is reachable from the More screen ("Se
  connecter / Créer un compte" / "تسجيل الدخول / إنشاء حساب").
- The screen is fully localized via `R.string.auth_*` keys (FR + AR).
- The "Continuer en invité" / "متابعة كضيف" action is the **primary
  CTA** in the screen, and is always available. The user is never
  forced through registration before reaching the app.
- A `GuestStatusBanner` composable was added for future use inside the
  app to remind guest users that their data is local.
- `SupabaseClientRef` is initialised from `CarDiagModernActivity` so
  non-Composable code (e.g. coroutine error mapping) can resolve
  string resources via the application context.

#### 3. Hardcoded string cleanup
- Every `if (arabic) "..." else "..."` in production paths now has
  a string resource key.
- `VehicleProfileExact.kt` was rewritten so every user-visible label
  resolves through `stringResource(R.string.vp_*)`. 40 new FR + AR
  keys were added (`vp_loading_error`, `vp_tab_overview`, …).
- `mapAuthError` in `AuthScreens.kt` now resolves all error messages
  via `R.string.auth_failed_*` and only falls back to the raw
  exception message when no specific key matches.
- `ai_unavailable_fallback` resolves a localised string instead of an
  inline `if (arabic) ...` ternary.
- `AuthScreen.kt` (legacy) is removed from the production graph.

#### 4. Edge function / AI localisation
- The `offlineDiagnosis` and `validateDiagnosis` paths in
  `supabase/functions/diagnose/index.ts` already return language-
  specific strings. A new test
  `EdgeFunctionOfflineLocalizationTest` asserts the structure: every
  recommended-tests / repair / safety / uncertainty / next-best-test
  field is computed via `isAr ? [ar] : [fr]` with both blocks
  containing genuine Arabic + Latin script.
- The client-side `DiagnosticService` and `AiDiagnosisScreen` already
  surface `ai_service_error` / `ai_service_timeout` /
  `ai_unavailable_fallback` in the active language.

#### 5. New tests
- `RoadAssistantTest` (8):
  - offline provider never fabricates businesses (no address, phone,
    rating, distance),
  - offline hazards provider returns no hazards,
  - service snapshot aggregates providers,
  - empty snapshot when location is unavailable,
  - `ServiceCategory.fromKey` and `HazardKind.fromKey` are stable,
  - Arabic + French search queries contain the expected keywords.
- `RoadAssistantLocalizationTest` (5): required keys exist in both
  languages, Arabic copy uses Arabic script, FR/AR differ, FR/AR have
  matching `ra_*` key sets.
- `AuthScreenLocalizationTest` (4): all auth-related keys exist in
  both languages, Arabic uses Arabic script, FR/AR differ for guest
  banner + invalid-credentials.
- `EdgeFunctionOfflineLocalizationTest` (4): every localized field in
  `offlineDiagnosis` has an Arabic + French branch, Arabic copy uses
  Arabic script, French copy uses Latin script.
- `LocationProviderTest` (6): service-category and hazard-kind keys
  are stable, offline provider is non-live, every category has a FR +
  AR description, every key category has Arabic + French search
  queries, Android manifest declares the two location permissions.
- All previous 113 tests still pass.

### Validation evidence
- `./gradlew --no-daemon testDebugUnitTest` — 141 tests, 0 failures.
- `./gradlew --no-daemon compileDebugKotlin` — success.
- `./gradlew --no-daemon lintDebug` — success, 0 errors.
- `./gradlew --no-daemon assembleDebug` — `app-debug.apk` produced.
- `./gradlew --no-daemon assembleRelease bundleRelease` —
  `app-release-unsigned.apk` and `app-release.aab` produced.
- `bash verify.sh` — passes end-to-end.

### Non-negotiable rules preserved
- RLS, permissions, tests, security controls and validation have not
  been weakened to obtain a green build.
- No automotive fact, DTC meaning, ECU compatibility, sensor value,
  price, local service, address, vehicle specification, diagnostic
  result or test result has been fabricated.
- The Road Assistant is **explicitly** a curated offline catalog. Live
  providers (Overpass, HERE, Mapbox) can be plugged in later via the
  provider interfaces without changing the UI. The architecture
  forbids the app from inventing business / address / phone / rating
  / distance data; this is asserted in the new test suite.
- The `offlineDiagnosis` Edge Function path returns language-specific
  strings for every user-visible field; the previous French-only
  fallback is gone.
- No secret, signing credential, API key or token has been added to
  the Android sources.
- The diagnostic report still shows the truthful "no comparison
  available" message when the user has not recorded a before/after
  pair.

## Highest-priority backlog for the next cycle
1. Wire a real (live) `NearbySearchProvider` + `HazardsProvider` for
   the Algerian market (Overpass, HERE, or a curated business dataset
   with verifiable addresses and phone numbers).
2. Replace the placeholder `vehicle_silhouette` with generation-aware
   generated artwork once a small, on-device generative pipeline is
   available; for now the silhouette + per-model image_url from
   `vehicle_models` is the production fallback.
3. Add a `LiveMeasurement`-based structured Freeze Frame view in
   addition to the raw dump.
4. Add OBD hardware validation on a maintained CI runner.
5. Expand the offline DTC catalog with more B/C/U codes and richer
   repair guidance, without inventing facts.
6. Continue RTL accessibility audit (touch targets, contrast,
   semantic labels, dynamic font scale).
