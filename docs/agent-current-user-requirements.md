# CarDiag DZ — Current User Requirements Addendum

This addendum is mandatory for the current autonomous engineering cycle. It comes directly from the latest hands-on APK review. Treat these as concrete acceptance requirements in addition to the existing professional transformation mission.

## Highest-priority fixes

1. Arabic is not complete. Audit the installed APK experience and repository, then make Arabic a first-class RTL experience across the entire production app.
2. Registration/login must be OPTIONAL. A user must be able to enter and use the app as a guest. Account creation may be offered for sync/history/cloud features, but must never block the core app.
3. DTC currently exposes English/incorrectly localized content in places. Remove unintended English from production UI and diagnostic results. DTC titles, descriptions, causes, diagnostic steps, repair guidance, warnings and labels must follow the selected language.
4. AI diagnosis currently does not provide a reliable working experience. Trace the full Android -> Supabase -> Edge Function -> OpenAI -> response validation -> UI path, fix the real cause, and test success, timeout, API failure and fallback.
5. When the selected language is Arabic, AI diagnosis MUST be returned/displayed in Arabic. When French is selected, it MUST be French. Never expose English as an accidental fallback. The current server fallback contains French strings even for Arabic; fix this comprehensively.
6. Vehicle images are missing. Implement a scalable vehicle-artwork solution using generated/original assets rather than copying random copyrighted website images. Use consistent professional automotive imagery, store/reference assets properly, and provide a graceful fallback when an image is unavailable. Do not invent trademarks or vehicle facts.
7. Mode Mécanicien is missing as a real product mode. Implement it as a genuine professional technician workflow, not merely a label/theme change.
8. Mode Conducteur / ordinary-user mode is missing as a distinct UX. Implement a simpler user flow that hides unnecessary technical complexity while preserving access to useful diagnosis.

## Required user flows

First launch should be coherent:
Language -> Mode Conducteur or Mode Mécanicien -> Continue as Guest.

The user must not be forced through registration before reaching the app.

## Mode Conducteur

Prioritize:
- My vehicle
- Start diagnosis
- Describe a problem/symptom
- Scan with OBD when available
- DTC explanation
- What does this mean?
- Can I drive?
- What should I do next?
- Diagnostic history
- Find a mechanic/service when the feature/data exists

Use plain Arabic/French. Do not expose raw ECU/PID terminology on primary screens unless useful.

## Mode Mécanicien

Provide a professional workspace with:
- Vehicle/customer context
- OBD scan
- ECU/system status
- Active/pending/permanent DTC
- VIN
- Live Data
- Freeze Frame
- Readiness
- Guided diagnosis
- AI diagnosis
- Measurements/tests and their results
- Evidence collection
- Repair guidance
- Clear + rescan workflow
- Before/after verification
- Diagnostic history
- Technician notes
- Professional diagnostic report/share

## DTC and diagnosis quality

DTC must not be a dictionary-only feature. Correlate:
vehicle + engine + ECU + DTC + symptoms + complaint + freeze frame + live data + measurements + knowledge base + performed tests.

Never recommend replacing a component solely because its name appears in a DTC. Prefer discriminating tests. Clearly distinguish evidence, rules/database knowledge, calculated values, user input and AI interpretation.

## History integrity

Audit the current History implementation and remove any hard-coded/demo diagnostic sessions, DTCs or before/after values. History must display real saved sessions/results. Do not present sample data as user data.

## Additional audit requirement

Do another full repository audit yourself. Look for issues the user did not explicitly list: unfinished screens, dead buttons, placeholders, duplicate/parallel production paths, incorrect navigation, hard-coded strings, broken RTL, fake/demo data, missing loading/error/empty states, broken backend paths, insecure API handling, weak RLS, OBD edge cases, unsupported PID handling, poor accessibility, poor adaptive layouts, missing tests and release problems.

Do not stop after fixing these named items. Fix other genuine blockers discovered during the audit, while preserving valid existing functionality and never fabricating automotive data.

## Definition of done for this addendum

The installed APK experience should be able to:
- open without mandatory registration;
- work in Arabic RTL and French LTR;
- let the user choose Conducteur or Mécanicien mode;
- show DTC content in the selected language;
- perform AI diagnosis through the real backend path or provide a truthful localized fallback;
- return Arabic AI results when Arabic is selected;
- show professional vehicle imagery or a clean fallback;
- save and reopen real diagnostic history;
- keep technical tools available to mechanics without overwhelming ordinary users;
- contain no accidental English, mock results or unfinished production paths.

Validate the result with tests/builds and record what was actually verified in agent-state.md. Do not claim runtime behavior was tested unless it was actually tested.