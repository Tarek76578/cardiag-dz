# Autonomous Agent State

The repository now uses exactly one GitHub Actions workflow for autonomous engineering: `.github/workflows/cardiag-agent-manual.yml`. The Android project root is `android/`.

## Operating model
- Start the agent manually from GitHub Actions with `workflow_dispatch`.
- The single agent is responsible for research, audit, implementation, tests, Android builds, release artifacts and repository state.
- The workflow does not use a push trigger, so an agent push cannot create an infinite self-triggering loop.
- The agent must use evidence, current external research and real validation. It must not invent automotive facts, diagnostic procedures, prices, service availability or test results.

## Known baseline from previous cycles
- Kotlin compilation: previously clean.
- Unit tests: previously 67/67 passing across 9 suites.
- Lint debug: previously reached 0 warnings, with dependency advisories tracked separately.
- Debug and release artifacts were previously buildable; release artifacts were unsigned.
- Data extraction/backup rules were previously added.
- VIN validation and diagnostic-correlation coverage were previously expanded.

## Highest-priority audit areas
1. Emulator/runtime and end-to-end UI coverage.
2. Arabic RTL and French localization completeness, including hard-coded Compose strings.
3. Accessibility: semantics, TalkBack order, contrast and touch targets.
4. OBD/Bluetooth lifecycle, ELM327 error handling and diagnostic safety.
5. Vehicle catalog correctness and Algeria-first coverage using verifiable sources.
6. Supabase authentication, RLS, privacy and offline-first behavior.
7. Release signing and Play readiness.
8. Dependency freshness and Gradle reproducibility.
9. Performance, startup, battery and crash/error handling.
10. Product differentiation versus major OBD competitors.

## Agent requirement
Every autonomous run must update this file with the date, evidence, research sources, findings, changes, exact validation results, unresolved blockers and next priorities. Never mark the project complete merely because a build succeeds.