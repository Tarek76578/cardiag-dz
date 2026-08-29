# CarDiag DZ — Explicit User-Priority Product Requirements

These are mandatory product requirements requested by the product owner. They must be implemented as real production functionality where technically supported, not treated as optional suggestions or an audit-only checklist.

## 1. REAL GPS
- Implement real Android location using appropriate Location APIs and runtime permissions.
- Obtain actual user location when permission is granted.
- Handle permission denied, location disabled, unavailable location and network failure gracefully.
- Never use fake coordinates or pretend demo locations are real.
- Provide a truthful path for nearby mechanics, auto electricians, dépannage/roadside assistance and spare-parts services using real service data when available.
- Show real distance only when calculated from real coordinates.
- Provide external navigation handoff for a selected real service.
- If a real local-service data source is not available yet, implement a clean repository/provider abstraction and truthful empty/unavailable state instead of inventing businesses, addresses or distances.
- Request only the location permissions actually needed and respect privacy.

## 2. ARABIC + FRENCH
- Arabic is a first-class production language with genuine RTL behavior.
- French remains fully supported.
- Add first-run language selection and persistent language selection in Settings.
- Localize production navigation, buttons, diagnostics, DTC, OBD, history, settings, errors, dialogs, loading/empty states and accessibility text.
- Remove unintended English/mixed-language production UI.
- Use proper Android localization resources/architecture rather than scattered hard-coded language conditionals.
- Verify semantic order, alignment, spacing and directional icons for Arabic RTL.

## 3. LOGIN OPTIONAL
- Login/register must NOT be required to enter or use core CarDiag functionality.
- Provide a clear Continue as Guest path.
- Authentication can be offered for optional account/synchronization features.
- Auth failure, expired session or no internet must not unnecessarily block guest use.
- Preserve secure session handling and database/RLS security.

## 4. AI VEHICLE IMAGES
- When a suitable verified vehicle image is unavailable, implement a secure AI image-generation/integration path.
- Generated imagery must correspond truthfully to the requested make/model/category when possible and must be labeled as generated where appropriate; never present generated images as verified OEM photography.
- Never place image-generation API keys in the APK, resources, BuildConfig or source code.
- Use secure server-side/backend integration for secrets.
- Implement loading, caching, error and unavailable states.
- Do not generate random imagery merely to make the UI appear complete.

## 5. PROFESSIONAL COMMERCIAL PRODUCT
The objective is a commercial-quality CarDiag product capable of competing on UX, clarity and diagnostic workflow with established automotive applications. Do not copy proprietary designs or content. Build a distinct CarDiag identity that is modern, automotive, technical and trustworthy.

Required qualities:
- coherent Material 3/Compose design system
- professional dashboard and navigation
- clear vehicle context
- unified diagnostic workspace
- DTC and symptom-based diagnosis
- guided tests and evidence
- diagnostic history
- polished loading/empty/error/offline states
- responsive layouts
- accessibility
- good performance
- no dead buttons or fake values in production paths
- no developer/debug presentation in normal user flows

## EXECUTION RULE
Do not stop at an audit, mock screen or documentation change. Implement, integrate, test and build real functionality. If a requirement is blocked by unavailable hardware, backend, API or real-world service data, implement the best truthful production architecture around that limitation and document the exact blocker in `agent-state.md`.

Never fabricate GPS locations, mechanics, service listings, diagnostic results, automotive specifications, DTC facts or vehicle identity.

Prioritize these requirements together with the existing mission and current-user requirements. Preserve all valid previous work and continue from `agent-state.md` rather than restarting the project.