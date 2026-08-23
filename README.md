# CarDiag DZ

CarDiag is an Arabic/French automotive diagnosis platform focused on the Algerian market.

## Current stack

- Android: Kotlin + Jetpack Compose + Material 3
- Backend: Supabase Edge Functions
- Database: Supabase PostgreSQL
- AI: OpenAI through the `diagnose` Edge Function
- Vehicle catalog: Supabase vehicle models, generations, specifications and images
- OBD-II: Bluetooth Classic ELM327 SPP transport
- Languages: Arabic / Français with Android locale RTL support

## Diagnostics flow

1. Anonymous Supabase authentication creates a user session.
2. The app loads the vehicle catalog from PostgreSQL.
3. A diagnostic session is created in `diagnostic_sessions`.
4. The `diagnose` Edge Function validates the authenticated session.
5. Symptoms, DTCs and measurements are sent to the AI layer.
6. The structured diagnosis is saved in `diagnostic_results`.
7. The UI displays summary, severity and confidence with a safety disclaimer.

## OBD-II

CarDiag now contains a real Bluetooth Classic ELM327 transport. Pair the adapter with Android, grant Bluetooth permissions, connect, initialize the ELM327 and request standard OBD-II PIDs/DTCs.

The transport is hardware-dependent: a successful software build cannot prove communication with a physical adapter. A real vehicle + compatible ELM327 adapter is required for hardware validation.

## Production release

The CI workflow builds:

- Debug APK
- Release APK
- Release Android App Bundle (AAB)

For a Play Store-ready signed build, configure these GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `SUPABASE_URL`
- `SUPABASE_PUBLISHABLE_KEY`

The signing keystore must never be committed to the repository.

## Database status

The connected Supabase project currently contains the core vehicle and diagnostic schema, including vehicle makes/models/generations/images, DTCs, faults, symptoms, repairs, OBD PIDs and diagnostic sessions/results.

The mobile client is intentionally resilient when a vehicle image is missing: it displays the bundled CarDiag fallback artwork instead of a broken image.
