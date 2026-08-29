# CarDiag DZ — Current User Requirements Addendum

This addendum is mandatory for the current autonomous engineering cycle. It comes directly from the latest hands-on APK review. Treat these as concrete acceptance requirements in addition to the existing professional transformation mission.

## Highest-priority fixes

1. Arabic is not complete. Audit the installed APK experience and repository, then make Arabic a first-class RTL experience across the entire production app.
2. Registration/login must be OPTIONAL. A user must be able to enter and use the app as a guest. Account creation may be offered for sync/history/cloud features, but must never block the core app. Add a clearly visible Login / Create account action.
3. DTC currently exposes English/incorrectly localized content in places. Remove unintended English from production UI and diagnostic results. DTC titles, descriptions, causes, diagnostic steps, repair guidance, warnings and labels must follow the selected language.
4. AI diagnosis currently does not provide a reliable working experience. Trace the full Android -> Supabase -> Edge Function -> OpenAI -> response validation -> UI path, fix the real cause, and test success, timeout, API failure and fallback.
5. When the selected language is Arabic, AI diagnosis MUST be returned/displayed in Arabic. When French is selected, it MUST be French. Never expose English as an accidental fallback. The current server fallback contains French strings even for Arabic; fix this comprehensively.
6. Vehicle images are missing. Implement a scalable vehicle-artwork solution using generated/original assets rather than copying random copyrighted website images. For each supported vehicle/model (for example Audi A4), provide a professional image representing the correct vehicle generation when possible, with a clean fallback when unavailable. The product may use internet references to identify the correct generation/style, but must not simply copy random copyrighted website images. If AI generation is used, make it actually work and clearly treat generated imagery as illustrative rather than a real photograph.
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
- Find a mechanic/service
- Road Assistant / GPS

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

## NEW: GPS Road Assistant — major product feature

CarDiag must become a practical road assistant for drivers in Algeria, not just an OBD diagnostic application.

Implement a dedicated "Road Assistant" / "مساعد الطريق" experience using the device location only after requesting the appropriate Android location permission.

The driver should be able to find nearby:
- Mechanics / garages
- Auto electricians
- Dépannage / roadside assistance
- Spare-parts shops
- Fuel stations
- Hospitals/emergency services when appropriate
- Other relevant automotive services available from a trustworthy data source

For nearby results, provide where the underlying data supports it:
- Distance from the driver
- Map position
- Directions / navigation hand-off
- Phone/call action when a public number exists
- Opening hours when available
- Rating/review information when provided by the source
- Service category

Allow sorting/filtering by nearest distance and category. Search should understand Arabic and French terms such as ميكانيكي, ميكانيكي سيارات, كهربائي سيارات, dépannage, garage, pièces détachées, قطع الغيار.

### Road hazards and driver assistance

Provide a road-assistance area that can display or accept reports for relevant road situations when a reliable data source is available, such as:
- Accident
- Road closure
- Obstacle
- Vehicle stopped/broken down on the road
- Pothole / dangerous road condition
- Traffic/road disruption when reliable live data exists

Never fabricate live road hazards, business availability, addresses, prices, phone numbers or ratings. If a live data provider is required, implement a clean provider abstraction and document the exact external dependency instead of inventing data.

### GPS behavior and privacy

- Request location permission contextually, not blindly at startup.
- Handle denied location permission gracefully; the rest of CarDiag must continue working.
- Show a clear explanation for why location is needed.
- Do not persist precise location unnecessarily.
- Handle unavailable GPS, timeout, network failure and empty nearby results with useful localized states.
- Do not claim navigation/live traffic functionality unless the implementation actually works.

### GPS architecture

Build the GPS/service layer so it can later support multiple trustworthy providers without rewriting the UI. Separate location acquisition, nearby-place search, road-hazard data and navigation hand-off behind maintainable interfaces.

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
- provide a functional Road Assistant/GPS flow or clearly document the exact external data dependency if live nearby/road data cannot yet be supplied;
- handle location permission denial and unavailable location without crashing;
- contain no accidental English, mock results or unfinished production paths.

Validate the result with tests/builds and record what was actually verified in agent-state.md. Do not claim runtime behavior was tested unless it was actually tested.