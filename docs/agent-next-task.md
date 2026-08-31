# CarDiag GPS Map Production Milestone

## Mission — REAL GPS MAP ONLY
Implement **only the REAL GPS/location map feature inside the existing CarDiag Android application**.

The goal of this autonomous cycle is a production-quality map screen that obtains the user's **actual device location** through Android's real location APIs with explicit runtime permission and displays it on an interactive map using the project's existing architecture/dependencies where possible.

## Required work
- Inspect the existing Android project and identify the current Road Assistant/location/map code before editing.
- Implement or complete the GPS map UI in the existing CarDiag design system.
- Use Android's actual location APIs and the device's real location providers.
- Request and handle runtime location permission correctly, only when the GPS map feature needs it.
- Display the user's **real current latitude/longitude** on the interactive map when permission is granted and a valid device location is available.
- Provide a clear current-location marker based on the actual device location.
- **DO NOT use mock locations, hardcoded coordinates, simulated coordinates, fake GPS data, demo coordinates, or placeholder current-location data.**
- Handle permission denial, location services disabled, unavailable location, provider errors, network/map loading errors, and lifecycle safely without crashing.
- Provide a sensible fallback/default map position only when real GPS is unavailable; never present the fallback as the user's actual location.
- Do not persist the user's location.
- Do not introduce background location tracking.
- Do not add continuous tracking unless strictly required for the visible current-location map behavior; prefer a bounded/current-location request.
- Preserve Arabic RTL and French localization.
- Reuse existing dependencies and map/location infrastructure where possible. Do not introduce unrelated libraries or architecture rewrites.
- Add/update focused regression tests for GPS/location state, permission handling, and error handling where the existing test architecture permits.
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
- OpenRouter/Gateway/provider/model rotation logic

## Completion rule
This is **one task only: REAL GPS + interactive map**.
When the real GPS map feature is implemented, tested, and validated, **STOP immediately**. Do not continue to another backlog item. Do not commit or push; GitHub Actions owns the verified commit.
