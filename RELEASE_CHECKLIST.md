# CarDiag DZ 1.0 Release Checklist

## CI

- [x] Debug APK build
- [x] Automated debug unit-test task wired into CI
- [x] Android lint task wired into CI
- [x] Release APK build wired into CI
- [x] Release AAB build wired into CI
- [x] Release artifacts uploaded by CI

## App

- [x] Modern dark Material 3 UI
- [x] Arabic and French language controls
- [x] Android RTL support
- [x] Supabase vehicle catalog loading
- [x] Resilient vehicle-image loading with local fallback
- [x] Supabase anonymous authentication flow
- [x] AI diagnostic client
- [x] Bluetooth ELM327 OBD transport
- [x] DTC request, RPM PID and coolant-temperature PID transport methods

## Backend / data

- [x] Diagnostic session persistence
- [x] AI Edge Function present
- [x] AI result persistence
- [x] Vehicle catalog tables present
- [x] Diagnostic-code tables present
- [x] OBD PID tables present

## Remaining external release validation

- [ ] Configure production Android signing secrets in GitHub
- [ ] Build a signed AAB with the production keystore
- [ ] Install the release APK on a physical Android device
- [ ] Test Arabic RTL on a physical device
- [ ] Test French UI on a physical device
- [ ] Test anonymous authentication against the live Supabase project
- [ ] Test AI diagnosis against the live Edge Function with the production OpenAI secret
- [ ] Pair a real ELM327 adapter and verify connection
- [ ] Read real DTCs from a vehicle
- [ ] Verify RPM and coolant PID responses on a running vehicle
- [ ] Expand vehicle image coverage beyond the currently populated image URLs
- [ ] Complete Play Console listing, privacy policy, data-safety form and store assets

A signed Play Store artifact cannot be created safely until the production keystore is supplied through GitHub Actions secrets. A real OBD diagnosis cannot be certified without a physical ELM327 adapter and vehicle.
