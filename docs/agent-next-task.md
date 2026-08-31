# CarDiag GPS Map Production Milestone

## Mission — GPS MAP ONLY
Implement **only the GPS/location map feature inside the existing CarDiag Android application**.

The goal of this single autonomous cycle is a production-quality map screen that can obtain the user's current location with explicit permission and display it on an interactive map using the project's existing architecture/dependencies where possible.

## Required work
- Inspect the existing Android project and identify the current Road Assistant/location/map code before editing.
- Implement or complete the GPS map UI in the existing CarDiag design system.
- Display an interactive map centered on the user's current location when permission is granted and a valid location is available.
- Provide a clear current-location marker.
- Provide a sensible fallback/default map position when GPS is unavailable or permission is denied.
- Handle location permission denial, unavailable location, provider errors, network/map loading errors, and lifecycle safely without crashing.
- Keep location permission least-privilege and request it only when the GPS map feature needs it.
- Do not persist the user's location.
- Do not introduce background location tracking.
- Do not add continuous tracking unless it is strictly required for the visible current-location map behavior; prefer a bounded/current-location request.
- Preserve Arabic RTL and French localization.
- Reuse existing dependencies and map/location infrastructure where possible. Do not introduce unrelated libraries or architecture rewrites.
- Add/update focused regression tests for the GPS/location state and error handling where the existing test architecture permits.
- Run relevant unit tests, lint, and Android debug build. Fix actual failures; do not weaken or bypass validation.
- Update `agent-state.md` with the actual GPS/map implementation and validation evidence.

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
This is **one task only: GPS + interactive map**.
When the GPS map feature is implemented, tested, and validated, **STOP immediately**. Do not continue to another backlog item. Do not commit or push; GitHub Actions owns the verified commit.
