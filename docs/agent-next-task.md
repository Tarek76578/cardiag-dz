# CarDiag Road Assistant Production Milestone

## Mission
Complete the next coherent production milestone for the Road Assistant and nearby-service experience for the Algerian market. Work across the existing project as needed rather than being restricted to a fixed file list.

## Scope
- Inspect the existing Road Assistant implementation, UI/state flow, networking, localization, tests, and project conventions before editing.
- Implement the largest coherent production-quality increment that can be completed and validated in this autonomous cycle.
- Modify any project files required by the milestone, but do not make unrelated changes or rewrite stable code unnecessarily.

## Functional requirements
- Provide a real nearby-service provider backed by the public Overpass API using the existing Ktor HTTP and kotlinx.serialization dependencies.
- Build a small Overpass query from the user's coarse latitude/longitude, selected `ServiceCategory` values, and radius.
- Parse only verifiable OSM elements and map only fields actually returned by OSM into the existing domain models.
- Never fabricate business names, addresses, phone numbers, ratings, opening hours, coordinates, distances, automotive data, pricing, or road hazards.
- Keep `OfflineNearbyProvider` available as an explicit fallback; never silently label offline data as live.
- Preserve existing public interfaces and `RoadAssistantSnapshot` semantics unless a change is required by the milestone.
- Handle network, HTTP, timeout, malformed JSON, and API failures safely without crashing the app.
- Use bounded network and connection timeouts.
- Do not persist the user's location and do not introduce background location tracking.
- Do not implement live road hazards in this milestone.

## Product requirements
- Preserve Arabic RTL and French localization.
- Preserve least-privilege permissions and existing security boundaries.
- Reuse existing dependencies; do not add a dependency unless the repository already requires it and the task cannot reasonably be completed without it.
- Keep UX behavior consistent with the existing CarDiag design system.

## Validation requirements
- Add or update regression tests when appropriate.
- Run the strongest relevant unit tests and Android checks available.
- Run Android lint and debug build.
- Run release build/bundle validation when feasible.
- If validation fails, diagnose the actual failure from logs/output and fix it. Do not weaken, skip, or bypass tests.
- Review the complete diff for accidental files, secrets, regressions, unrelated changes, RTL/localization problems, and generated artifacts.

## Completion requirements
- Update `agent-state.md` with completed work, validation evidence, remaining blockers, and next priorities.
- Do not commit or push from Codex; the workflow owns commits and pushes.
- Do not declare the product production-ready unless the final regression review is green and known critical blockers are resolved.
