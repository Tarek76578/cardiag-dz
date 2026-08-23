# CarDiag DZ Production Release Checklist

## Engineering

- [x] Android 16 / API 36 target
- [x] Material 3 application shell
- [x] Arabic RTL and French localization flow
- [x] Dark/light theme
- [x] Accessibility content descriptions on interactive vehicle/diagnostic imagery and navigation
- [x] Offline vehicle catalog cache and offline state
- [x] Loading/skeleton and retry states
- [x] Email/password authentication
- [x] User garage and VIN validation
- [x] Diagnostic history
- [x] Structured AI diagnostic flow
- [x] Bluetooth Classic ELM327 transport
- [x] DTC/RPM/coolant/speed parsing
- [x] Pending DTC / clear DTC / supported PID / VIN commands
- [x] Network timeout and retry handling
- [x] R8/shrinker configuration
- [x] Unit tests and lint in CI
- [x] Debug APK + release APK + AAB CI artifacts
- [x] Repository secret scan

## Data / security

- [x] Supabase RLS enabled on user-owned diagnostic tables
- [x] Anonymous JWTs rejected by user-owned RLS policies
- [x] Anonymous SELECT revoked from private catalog helper tables
- [x] Database integrity constraints for mileage/year/language
- [x] Production indexes for common access patterns
- [x] Bilingual common DTC knowledge expanded
- [x] Supabase Edge Function JWT validation
- [ ] Enable Supabase leaked-password protection in Authentication settings
- [ ] Configure production SMTP and email confirmation policy

## Release credentials / external validation

- [ ] Configure production Android signing secrets in GitHub
- [ ] Build and verify a signed AAB with the production keystore
- [ ] Install release APK on a physical Android device
- [ ] Test Arabic RTL and French on the physical device
- [ ] Test real account signup/sign-in and email confirmation
- [ ] Test AI diagnosis with the production Edge Function
- [ ] Pair a real ELM327 adapter and verify connection/reconnection
- [ ] Read real current and pending DTCs from a vehicle
- [ ] Verify RPM/coolant/speed on a running vehicle
- [ ] Test clear-DTC only on a controlled test vehicle
- [ ] Expand `vehicle_images` coverage for the remaining catalog models with verified model-specific assets
- [ ] Publish privacy policy at a stable HTTPS URL
- [ ] Configure support contact and account/data deletion flow
- [ ] Complete Play Console Data Safety and content rating
- [ ] Capture production screenshots and feature graphic
- [ ] Complete closed testing before production rollout

Google Play requires API level 36 for new apps and app updates submitted from August 31, 2026.
