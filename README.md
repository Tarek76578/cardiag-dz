# CarDiag DZ

CarDiag is an Arabic/French automotive diagnosis platform focused on the Algerian market.

## Current stack

- Android: Kotlin + Jetpack Compose + Material 3
- Backend: Supabase Edge Functions
- Database: Supabase PostgreSQL with RLS
- AI: OpenAI through the authenticated `diagnose` Edge Function
- Vehicle catalog: Supabase vehicle models, generations, specifications and images
- OBD-II: Bluetooth Classic ELM327 SPP transport with standard PID/DTC parsing
- Languages: Arabic / Français with RTL support
- Offline: cached vehicle catalog with explicit offline state

## Authentication and security

Production app flows use email/password authentication. Anonymous sign-in is not used by the Android client. User-owned vehicle and diagnostic tables require a permanent authenticated user and RLS rejects anonymous JWTs. Service-role credentials are never shipped to the Android application.

## Diagnostics flow

1. The user signs in or creates an account.
2. The app loads the vehicle catalog from PostgreSQL and caches it locally.
3. A diagnostic session is created in `diagnostic_sessions`.
4. The `diagnose` Edge Function validates the authenticated session.
5. Symptoms, DTCs and measurements are sent to the AI layer with a structured response contract.
6. The structured diagnosis is saved in `diagnostic_results`.
7. The UI displays the structured response with safety context.

## OBD-II

CarDiag contains a real Bluetooth Classic ELM327 transport. Pair a compatible adapter with Android, grant Bluetooth permissions, connect, initialize the ELM327 and request standard OBD-II data.

Supported transport operations include current DTCs (Mode 03), pending DTCs (Mode 07), clear DTC (Mode 04), RPM, coolant temperature, vehicle speed, supported-PID discovery and VIN information. Hardware validation still requires a physical ELM327 adapter and a compatible vehicle ECU.

## Production release

CI verifies repository secrets, unit tests, lint and builds:

- Debug APK
- Release APK
- Release Android App Bundle (AAB)

Google Play's 2026 requirement is API level 36 for new apps and updates submitted from August 31, 2026, so the project targets Android 16 / API 36.

For a Play Store-ready signed build, configure these GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

The signing keystore must never be committed to the repository.

## Data and release readiness

The connected Supabase project contains the core vehicle and diagnostic schema, including vehicle makes/models/generations/images, DTCs, faults, symptoms, repairs, OBD PIDs and diagnostic sessions/results. The Android client handles missing images safely with bundled fallback artwork.

Before public Play Store publication, production secrets, a public privacy-policy URL, support contact, account/data deletion flow, screenshots and the final Play Console Data Safety declaration must be configured and tested.
