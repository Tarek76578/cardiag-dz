# CarDiag DZ — Design & Engineering Review

## Current target
- Arabic RTL + French UI
- Modern automotive dashboard
- Vehicle catalog with image-first cards
- AI diagnostic flow grounded in structured Supabase data
- OBD-ready architecture

## UI direction
- Dark automotive visual language
- High-contrast surfaces and restrained accent color
- Large vehicle hero card
- Primary diagnostic CTA
- Quick actions: OBD scan, DTC lookup, vehicle garage, history
- Bottom navigation: Home, Diagnose, Vehicles, History

## Image strategy
Vehicle image fields already exist in Supabase, but the current catalog has no populated image URLs. The app therefore needs a deterministic local fallback and a separate image ingestion pipeline. Wikimedia Commons is an acceptable source when the individual asset license and attribution are recorded.

## Security
Anonymous Auth remains enabled for frictionless onboarding, but sensitive user-owned data must remain scoped by auth.uid(). The anonymous-user security warnings should be reviewed before production launch; Supabase recommends distinguishing anonymous JWTs with is_anonymous and using anti-abuse controls such as CAPTCHA/Turnstile.
