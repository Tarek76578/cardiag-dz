# CarDiag DZ — Play Store package

## Short description
Diagnostic OBD-II, vehicle garage and AI-assisted fault analysis in Arabic and French.

## Full description
CarDiag DZ helps drivers identify vehicle faults before replacing parts. Add your vehicle, connect a compatible ELM327 Bluetooth adapter, read standard OBD-II trouble codes and live values, then combine the measured data with symptoms for an AI-assisted structured analysis.

### Features
- Arabic RTL and French UI
- Personal vehicle garage
- VIN validation
- OBD-II / ELM327 Bluetooth Classic support
- DTC and live-data parsing
- Diagnostic history
- AI-assisted analysis with structured output
- Offline vehicle catalog cache
- Retry and network error handling
- Dark and light themes

### Important
OBD results depend on the vehicle ECU, protocol and adapter. CarDiag is an assistance and information tool; it does not replace a qualified mechanic or manufacturer diagnostic equipment. Safety-critical repairs must be verified by a qualified professional.

## Data safety checklist
- Account: email address for authentication.
- User content: vehicle nickname, VIN, mileage and diagnostic history when the user chooses to save them.
- Diagnostics: OBD codes and measurements are uploaded only when the user starts a diagnostic session requiring cloud analysis.
- Do not collect contacts, precise location, advertising identifiers or unrelated device data.
- Provide account deletion and data deletion controls before publishing.

## Release checklist
- [ ] Production keystore configured as GitHub Actions secrets.
- [ ] Signed AAB downloaded from CI.
- [ ] Privacy policy published at a stable HTTPS URL.
- [ ] Support email configured.
- [ ] Account deletion tested.
- [ ] Play Console Data safety form completed from the actual production configuration.
- [ ] Screenshots captured from a physical Android device.
- [ ] Content rating completed.
- [ ] Closed testing completed before production rollout.
