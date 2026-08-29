# Next Atomic Agent Task

## Task
Implement the first production step of the Road Assistant backlog: a real live nearby-service provider for the Algerian market using the existing Ktor HTTP and kotlinx.serialization dependencies.

## Scope
Only these existing files may be modified:
1. `android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantService.kt`
2. `android/app/src/main/java/dz/cardiag/app/core/road/RoadAssistantProviders.kt`

No third file is allowed. Do not change Gradle because the project already contains `ktor-client-android` and `kotlinx-serialization-json`.

## Acceptance criteria
- Preserve the existing provider interfaces and `RoadAssistantSnapshot` contract.
- Add a real `NearbySearchProvider` implementation backed by the public Overpass API.
- Use HTTPS and a bounded request timeout.
- Build a small Overpass query from the user's coarse latitude/longitude, selected `ServiceCategory` values, and radius.
- Parse only verifiable OSM elements; never invent business names, addresses, phone numbers, ratings, opening hours, coordinates, or distances.
- Map supported OSM tags into the existing `NearbyService` model.
- If the network/API/JSON fails, return a typed failure or an empty result without crashing the app.
- Keep `OfflineNearbyProvider` available as an explicit fallback; do not silently label offline data as live.
- Do not implement live hazards in this task.
- Do not add new dependencies.
- Keep the diff targeted; do not rewrite unrelated code.

## Safety/privacy constraints
- No API keys or secrets.
- No background location.
- Do not persist the user's location.
- Do not fabricate Algerian businesses or road hazards.

## Required validation intent
The workflow, not the model, will run Gradle tests/lint/build after the edit. The model must inspect its own diff and stop if the two-file scope cannot be maintained.
