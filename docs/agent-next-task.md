# CarDiag GPS Map Production Milestone

## Mission — REAL GPS + INTERACTIVE MAP ONLY
Implement **only the GPS/location map feature inside the existing CarDiag Android application**.

The goal of this autonomous cycle is a production-quality map screen that obtains the user's **REAL device location** through Android location APIs after explicit runtime permission and displays it on an interactive map using the project's existing architecture/dependencies where possible.

## Required work
- Inspect the existing Android project and identify the current Road Assistant/location/map code before editing.
- Implement or complete the GPS map UI in the existing CarDiag design system.
- Use the real Android device location APIs (prefer the project's existing location infrastructure; use Fused Location Provider if already available/appropriate).
- Request and correctly handle `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` runtime permissions only when the GPS map feature needs them.
- Display an interactive map centered on the user's **actual current latitude/longitude** when permission is granted and a valid location is available.
- Provide a clear current-location marker representing the real device position.
- Do **NOT** use hardcoded coordinates as the user's location.
- Do **NOT** use mock/fake GPS data.
- Do **NOT** silently substitute a fake location when real location is unavailable.
- Provide a sensible non-user-location default map position only as a map viewport fallback; clearly distinguish it from the user's real location.
- Handle permission denial, permanently denied permission, location services disabled, unavailable location, provider errors, network/map loading errors, and lifecycle safely without crashing.
- Do not persist the user's location.
- Do not introduce background location tracking.
- For visible current-location behavior, use a lifecycle-aware bounded/current-location request or appropriately scoped updates; avoid unnecessary battery drain.
- Preserve Arabic RTL and French localization.
- Reuse existing dependencies and map/location infrastructure where possible. Do not introduce unrelated libraries or architecture rewrites.
- Add/update focused regression tests for GPS/location state, permission/error handling, and location-state transitions where the existing test architecture permits.
- Run relevant unit tests, lint, and Android debug build. Fix actual failures; do not weaken or bypass validation.
- Update `agent-state.md` with factual evidence: files changed, real-GPS implementation details, tests/build validation, and remaining work if any.

## Strict exclusions — DO NOT IMPLEMENT
Do not work on:
- OBD/Bluetooth hardware
- DTC/fault database
- AI diagnosis
- nearby mechanics/services or Overpass API
- road hazards
- spare parts/prices
- authentication
- payments
- notifications
- background location
- analytics/tracking
- unrelated UI redesign
- unrelated refactoring

## Completion rule
This is **one task only: REAL GPS + interactive map**.
When the GPS map feature is genuinely implemented, tested, and validated, **STOP immediately**. Do not claim completion if the app only contains GPS state/placeholders without obtaining a real device location. Do not continue to another backlog item. Do not commit or push; GitHub Actions owns the verified commit.
