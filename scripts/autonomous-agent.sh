#!/usr/bin/env bash
set -euo pipefail

: "${OPENROUTER_API_KEY:?OPENROUTER_API_KEY is required}"
export CODEX_HOME="${CODEX_HOME:-$PWD/.codex}"
mkdir -p "$CODEX_HOME"

PROMPT='You are the sole autonomous principal engineer, senior Android/Kotlin/Jetpack Compose engineer, product designer, UX architect, QA engineer, security reviewer, automotive diagnostics domain reviewer, researcher and release engineer for CarDiag DZ.

Read AGENTS.md and agent-state.md first. Inspect the ENTIRE repository before changing anything. This is a FULL PRODUCT TRANSFORMATION MILESTONE, not a small bug fix or cosmetic UI task.

PRIMARY MISSION
Transform the existing CarDiag DZ application into a coherent, professional, production-quality automotive diagnostic product for the Algerian market while preserving working functionality. The final experience must feel like ONE diagnostic platform, not a collection of disconnected tool screens.

CORE PRODUCT FLOW
Vehicle -> Problem/Symptom -> System -> OBD Scan when available -> DTC -> Guided Tests -> Evidence -> Diagnosis -> Repair Guidance -> Clear/Rescan -> Before/After Verification -> Diagnostic History.

Do not stop after improving one screen. Work through the complete milestone. If the 90-minute cycle cannot finish everything, make coherent progress, persist detailed state in agent-state.md, and the next autonomous cycle MUST continue from the remaining milestone backlog rather than declaring the product finished.

NON-NEGOTIABLE SAFETY/QUALITY RULES
- Preserve existing valid functionality; do not rewrite the project from scratch without evidence.
- Never fabricate automotive facts, DTC meanings, ECU compatibility, sensor values, prices, local services, addresses, vehicle specifications, diagnostic results, or test results.
- Never fake OBD/ECU responses or present demo data as real data.
- Clearly distinguish real ECU data, database knowledge, calculated values, user input, and AI interpretation.
- AI is an assistant, never the sole authority; never recommend blind parts replacement.
- Never expose, print, commit, or persist secrets.
- Never weaken RLS, permissions, tests, security controls, or validation merely to obtain green CI.
- Never delete required functionality to silence errors.
- Never modify the manual trigger workflow from inside this agent, and do not create another agent/workflow.
- Do not make speculative or activity-only commits.

PHASE 1 — DEEP AUDIT
Inspect all Kotlin, Compose, Activities, navigation, ViewModels/state, repositories/services, database/migrations, Supabase/auth/RLS, vehicle catalog, DTC data, OBD/Bluetooth/ELM327 code, VIN, Live Data, Freeze Frame, Readiness, AI diagnosis, Garage, History, localization, themes, resources, manifest/permissions, Gradle, tests, CI/CD and release configuration.

Specifically identify and consolidate duplicated/parallel implementations such as multiple dashboards, home screens, vehicle profiles, diagnostic hubs or experimental production paths. Choose one coherent production architecture and remove/isolate obsolete paths only when safe.

Build a prioritized transformation backlog, but for this milestone the following product areas are mandatory and must all be addressed before declaring completion.

PHASE 2 — ONE CAR DIAG DESIGN SYSTEM
Create/refactor ONE coherent Material 3/Compose design system covering colors, typography, spacing, shapes, elevation, buttons, cards, icon containers, chips, badges, status indicators, dialogs, top bars, forms, loading/empty/error/success states, vehicle cards, DTC cards, measurement cards and diagnostic components.

Visual direction: automotive + professional + technical + modern + trustworthy. Avoid generic banking UI, toy UI, generic AI UI, excessive decoration, excessive cards, oversized empty areas, random gradients, inconsistent buttons, or developer/debug-dashboard presentation.

Use strong hierarchy: primary action -> important result -> next action -> secondary information -> advanced/raw information.

PHASE 3 — UNIFIED NAVIGATION
Create a coherent primary navigation, approximately:
- Home: current vehicle, diagnostic health, Start Diagnosis, alerts, recent session, quick actions.
- My Vehicle/Garage: multiple vehicles, current vehicle, add/edit vehicle, vehicle profile, engine, ECU/OBD, known faults, history.
- Diagnose: the central diagnostic workspace.
- History: diagnostic sessions, DTCs, measurements, tests, repairs, before/after.
- More: settings, language, appearance, about, advanced tools.

Do not leave multiple competing dashboards active in production.

PHASE 4 — PROFESSIONAL HOME
Home must immediately establish vehicle context. Show current vehicle, engine/year when available, a defensible Diagnostic Health score, Start Diagnosis, OBD Scan, Symptom Diagnosis, DTC Lookup, recent diagnostic session and important alerts. Do not overload Home with raw PID data.

Health score MUST NOT falsely claim overall mechanical health. Base it only on available evidence such as active DTC severity, communication failures and readiness where appropriate, and provide an explanation of why the score has that value.

PHASE 5 — VEHICLE/GARAGE EXPERIENCE
Make vehicle context persistent and central. Support make -> model -> generation -> year -> engine -> fuel -> transmission where available. Current vehicle must flow automatically into diagnosis.

Vehicle profile should contain:
- Overview
- Engine
- Specifications when data exists
- ECU & OBD
- DTC & known faults
- Diagnostics
- History

Keep vehicle-specific relationships coherent. If vehicle-specific information is unavailable, explicitly state that information is generic.

PHASE 6 — REAL DIAGNOSTIC WORKSPACE
Create one central diagnostic experience.

Entry:
1. Select/confirm vehicle.
2. Select problem/symptom.
3. Choose system.
4. Choose OBD scan if hardware is available or continue symptom-based diagnosis.
5. Gather evidence.
6. Show likely causes based on evidence.
7. Recommend tests in useful order.
8. Record test results.
9. Narrow diagnosis.
10. Provide evidence-based repair guidance.
11. Clear/rescan only after evidence is saved.
12. Save session.

Do not force users to know a DTC before starting diagnosis.

PHASE 7 — SYMPTOM-BASED DIAGNOSIS
Provide selectable problem categories and symptoms, including as appropriate:
Engine: won't start, difficult starting, loss of power, hesitation, rough idle, misfire, high fuel consumption, excessive smoke, overheating, Check Engine.
Electrical: battery, alternator, starting, electrical fault, lights.
Brakes: ABS warning, brake pedal issue, braking vibration.
Airbag: SRS/airbag warning.
Transmission: shifting problem, automatic transmission warning/fault.
Cooling: overheating, coolant loss, fan problem.
DPF/emissions: DPF warning, regeneration problem, EGR issue, excessive emissions.
Also allow Other Symptom/free text.

Ask contextual questions only when relevant: constant/intermittent, acceleration/RPM, smoke, warning lights, limp mode, etc. Use answers to narrow causes and tests. Never claim certainty without evidence.

PHASE 8 — PROFESSIONAL OBD ONBOARDING
Redesign OBD as a guided flow, not a wall of technical buttons.

Before connection:
1. Turn ignition ON.
2. Plug OBD adapter.
3. Enable Bluetooth/required connection.
4. Select adapter.

Show available adapters and clear Connect action.

After connection provide adapter health:
- Bluetooth/connection
- adapter response
- ELM327 detection where applicable
- protocol detection
- ECU communication

Handle unsupported adapters/protocol errors gracefully and explain recovery steps.

PHASE 9 — PROFESSIONAL FULL VEHICLE SCAN
After successful connection show a clean scan workspace and progress through relevant systems where supported.

Results should be system-oriented:
Engine ECU, ABS, Airbag/SRS, Transmission, BCM/body and other supported systems.

Show statuses such as Critical faults, Attention, No faults, Unable to communicate. Do not interpret communication failure as a mechanical fault.

Do not put every raw PID/control on the first screen.

PHASE 10 — DTC EXPERIENCE
Make DTC a real diagnostic feature, not just a code list.

Support:
- DTC search
- browse
- filter by system
- filter by severity
- filter by category
- recent codes
- vehicle-specific faults where available
- P/B/C/U code families

DTC detail must include when data exists:
- code
- human-readable meaning
- system
- severity
- what it means
- symptoms
- possible causes
- recommended diagnostic tests
- evidence collected
- repair guidance
- safety notes

Never fabricate missing technical content.

PHASE 11 — GUIDED DIAGNOSTIC DECISION TREE
Where structured rules exist, convert diagnosis into Question -> Test -> Result -> Next Test.

Example pattern:
P0301 -> inspect spark plug -> passed/failed -> coil test -> fault follows/does not follow -> injector/compression/wiring as appropriate.

Record each result and use it to narrow recommendations. Do not simply tell users to replace parts.

PHASE 12 — LIVE DATA
Replace raw text-heavy Live Data with professional measurement cards.

Examples where actual supported data exists:
RPM, coolant, speed, throttle, battery, MAF, MAP and other supported PIDs.

Provide:
- readable values
- units
- normal/contextual range only when reliable
- status indication
- min/max where meaningful
- trend/mini charts
- favorite PIDs
- PID selection
- graph mode
- Overview vs Advanced Live Data

Unsupported PIDs must show Not Supported/Unavailable rather than fake values. Keep heavy work off the UI thread.

PHASE 13 — FREEZE FRAME
Present Freeze Frame as contextual evidence captured when the ECU stored a fault. Show useful measurements first and keep raw data under Advanced/Raw Data. Explain why it matters.

PHASE 14 — READINESS
Create a clear emissions/readiness screen with MIL status and monitor states such as Ready/Not Ready/Unsupported where supported. Explain Not Ready. Do not invent monitor status.

PHASE 15 — VIN
Present VIN as Vehicle Identity, including VIN and manufacturer/model/year only when actually available. Connect VIN to current vehicle profile when possible.

PHASE 16 — CLEAR DTC SAFETY FLOW
Move Clear DTC into Advanced actions. Correct sequence:
Scan -> save evidence -> diagnose -> repair -> clear -> rescan.

Before clearing, warn that codes and possibly Freeze Frame will be erased and readiness monitors may reset. After clearing, recommend Rescan. Never silently clear codes.

PHASE 17 — DIAGNOSTIC HISTORY
Create real diagnostic sessions, not just a generic activity log.

Store where supported:
- vehicle
- VIN
- date/time
- mileage if actually available
- active/pending/permanent DTCs
- Freeze Frame
- readiness
- measurements
- tests performed/results
- diagnosis
- repair
- notes

Allow reopening sessions.

PHASE 18 — BEFORE/AFTER
Support comparison when actual recorded evidence exists:
Before Repair -> DTCs/measurements/tests.
After Repair -> new scan/measurements.
Never manufacture before/after values.

PHASE 19 — AI DIAGNOSIS
Keep and improve AI integration. Feed AI relevant structured context: vehicle, engine, DTC, symptoms, tests, live data, freeze frame and history where available.

Render AI output into structured UI:
- Summary
- Likely causes
- Evidence
- Recommended tests
- Repair guidance
- Safety notes
- Confidence/uncertainty

Clearly distinguish AI interpretation from database/rule evidence. Do not expose giant raw JSON to normal users. Never claim AI confirmation when evidence is insufficient.

PHASE 20 — RULES + AI
Where rule-based automotive knowledge exists, use deterministic structured diagnostic rules alongside AI. Rules and actual evidence must take precedence over unsupported AI guesses. Clearly label sources of information.

PHASE 21 — ARABIC + FRENCH
Arabic and French are first-class languages.

Remove mixed-language production UI. Move user-facing strings into proper Android localization resources/architecture instead of scattering language conditionals through Composables.

Localize EVERYTHING: navigation, buttons, OBD, DTC, diagnostics, tests, dialogs, errors, loading, empty states, history, settings, AI, accessibility descriptions, warnings.

Arabic must be true RTL with correct semantic order, alignment, spacing, navigation and directional icons. Do not blindly mirror elements that should not mirror.

PHASE 22 — ADAPTIVE/RESPONSIVE UI
Use modern Android adaptive/window size concepts.

Compact: optimized phone layout.
Medium: larger phone/small tablet layout.
Expanded: tablet/landscape layout with useful two-pane experiences where appropriate, e.g. DTC list | DTC details or diagnostic steps | measurements.

Do not merely stretch a phone layout.

Audit small phones, normal phones, large phones, landscape and tablet-like widths for clipping, overflow, keyboard behavior, dialogs, scrolling, long vehicle names, long DTC descriptions, Arabic and French.

PHASE 23 — ACCESSIBILITY
Audit all production screens for usable touch targets, meaningful content descriptions, semantics, logical traversal/order, scalable text, contrast-conscious choices and no critical information conveyed only by color.

PHASE 24 — STATES
Every asynchronous or hardware/network/database operation must have professional:
- loading
- success
- empty
- error
- retry
- offline/unavailable
states.

Examples: no vehicle, no DTC, adapter unavailable, ECU communication failure, unsupported PID, backend timeout, malformed response, auth failure. Never expose stack traces to normal users.

PHASE 25 — OFFLINE/RESILIENCE
Where architecture supports it, preserve/cache useful vehicle/DTC information and diagnostic sessions. Preserve local test results during network loss. Retry safely. Do not claim offline support for features that still require network access.

PHASE 26 — SUPABASE/DATABASE
Inspect schema, migrations, RLS and data access. Ensure coherent support for vehicles, engines, ECUs, DTCs, symptoms, causes, tests, repairs, diagnostic sessions, measurements and relationships. Make only justified migrations, preserving backward compatibility where practical. Do not weaken RLS.

PHASE 27 — SECURITY
Audit secrets, logs, network handling, permissions and dependency configuration. Never move secrets into APK resources, BuildConfig or source. Handle malformed responses and authorization/network errors safely.

PHASE 28 — PERFORMANCE/ARCHITECTURE
Review unnecessary recomposition, large list loading, database queries, network calls, Bluetooth work, Activity churn, image loading, memory and battery behavior. Keep UI responsive.

Use ViewModels/UI state/repositories/services where appropriate without overengineering. Centralize navigation and reusable components. Do not put excessive application state directly into Composables.

PHASE 29 — AUTOMOTIVE DATA QUALITY
Audit vehicle catalog relationships and DTC data for internal consistency. Never fill gaps by invention. Preserve explicit unknown/unavailable states.

PHASE 30 — RESEARCH
Use fresh public web research available in the runner. Prefer official Android/Google documentation and authoritative OBD references, then reputable competitor documentation and current Algerian automotive-market sources. Compare the actual product experience against Carista, OBDeleven and Torque where useful. Record source URLs and access dates in market-audit.md. Do not copy proprietary content or invent facts.

PHASE 31 — REMOVE DEBUG/DEMO FEEL
Production paths must not expose developer dashboards, raw protocol dumps, placeholder controls, dead buttons, fake values, unfinished screens or contradictory UI. Advanced technical information remains available under appropriate Advanced/Raw Data areas.

PHASE 32 — TESTING
Add/update meaningful regression tests for changed domain/diagnostic logic. Validate:
1 first launch
2 home
3 vehicle selection
4 garage
5 vehicle profile
6 symptom diagnosis
7 diagnostic workspace
8 OBD disconnected state
9 adapter connection/error states
10 scan progress/results
11 DTC lookup/detail
12 guided diagnosis
13 Live Data
14 Freeze Frame
15 Readiness
16 VIN
17 Clear DTC confirmation
18 history
19 before/after where supported
20 AI diagnosis state handling
21 Arabic RTL
22 French
23 dark/light if supported
24 compact/expanded layouts.

Run relevant static checks, unit tests, lint, debug/release APK and AAB builds. Use emulator/runtime validation when infrastructure exists. If runtime validation is unavailable, document the exact limitation rather than claiming it passed.

PHASE 33 — BUILD/FIX LOOP
Use:
Inspect -> implement -> test -> build -> inspect failures -> fix actual cause -> rebuild -> review UX -> test again.

Do not stop because compilation succeeds. Do not suppress warnings/tests merely for green CI.

PHASE 34 — STATE/PERSISTENCE ACROSS AUTONOMOUS CYCLES
Update agent-state.md with:
- date
- milestone name
- complete audit findings
- completed work
- files/components changed
- validation evidence
- remaining mandatory areas
- blockers
- next exact priorities.

If time expires, leave a precise continuation plan. The next cycle must read agent-state.md and continue the professional transformation.

PHASE 35 — GIT/DELIVERY
Review complete diff for accidental changes, secrets, regressions, broken RTL/localization and unrelated modifications. Commit only safe verified improvements. Push production-quality changes to main when validation passes.

FINAL ACCEPTANCE CRITERIA
Do not declare this milestone complete merely because the app builds or one UI screen looks better.

CarDiag is complete for this milestone only when the repository has been genuinely transformed toward:
Vehicle -> Problem -> Diagnostic Workspace -> OBD/Rules when applicable -> DTC -> Guided Tests -> Evidence -> Diagnosis -> Repair -> Clear/Rescan -> Before/After -> History,
with one coherent design system, unified navigation, professional OBD UX, actionable DTC UX, symptom-based diagnosis, structured AI assistance, real vehicle context, complete Arabic RTL/French localization, adaptive layouts, accessibility, robust states, security, tests and validated builds.

If a feature is blocked by unavailable hardware/backend/database support, implement the best truthful production UX around that limitation and document the exact blocker. Never fabricate functionality.

CONTINUE until the current milestone is genuinely exhausted. Do not stop merely because one check is green.'

models=(
  "openrouter/free"
  "z-ai/glm-5.2:free"
  "minimax/minimax-m3:free"
  "nvidia/nemotron-3-ultra-550b-a55b:free"
  "openai/gpt-oss-120b:free"
)
max_attempts_per_model="${OPENROUTER_MAX_ATTEMPTS_PER_MODEL:-1}"
delay="${OPENROUTER_INITIAL_DELAY:-20}"
log_file="${RUNNER_TEMP:-/tmp}/codex-agent.log"

for model in "${models[@]}"; do
  cat > "$CODEX_HOME/config.toml" <<EOF
model = "$model"
model_provider = "openrouter"
approval_policy = "never"
sandbox_mode = "danger-full-access"

[model_providers.openrouter]
name = "OpenRouter"
base_url = "https://openrouter.ai/api/v1"
env_key = "OPENROUTER_API_KEY"
EOF

  for attempt in $(seq 1 "$max_attempts_per_model"); do
    echo "Starting autonomous Codex cycle with $model (attempt $attempt/$max_attempts_per_model)"
    set +e
    : > "$log_file"
    codex exec --dangerously-bypass-approvals-and-sandbox --skip-git-repo-check "$PROMPT" 2>&1 | tee "$log_file"
    status=${PIPESTATUS[0]}
    set -e

    if [ "$status" -eq 0 ]; then
      exit 0
    fi

    if grep -Eqi '(^|[^0-9])(400|404|408|409|429|500|502|503|504)([^0-9]|$)|Not Found|unavailable for free|Too Many Requests|rate limit|rate-limited|temporarily unavailable|capacity|provider.*unavailable|no available provider|Server tool request failed|unexpected argument|tool request.*bad request|HTTP 400|status: 400' "$log_file"; then
      echo "OpenRouter/model availability or compatibility failure for $model; moving to the next free candidate." >&2
      sleep "$delay"
      break
    fi

    echo "Codex failed for a non-recoverable reason; refusing to repeat the engineering cycle." >&2
    exit "$status"
  done
done

echo "All configured OpenRouter free models/router were unavailable, rate-limited, or incompatible." >&2
exit 1
