# CarDiag Vehicle Catalog Pipeline

The catalog is built as a provenance-first pipeline, not a single API dump.

## Sources

- **NHTSA vPIC**: vehicle identity / VIN-oriented reference data. It is the free baseline and should not be treated as the complete Algeria/EU catalog.
- **Wikimedia Commons**: images. Store the file URL, source URL, author and license for every imported image. Never bulk-copy an image without preserving its license metadata.
- **API Ninjas Cars**: optional paid enrichment for specifications. Do not put an API key in the Android app or source code.

## Data model

`make -> model -> generation -> engine -> trim -> ECU -> DTC/PID`

Images are attached at model/generation/trim level through `vehicle_images`.

## Import rules

1. Never overwrite verified records with lower-confidence source data.
2. Never infer a generation year range from a model's broad year range.
3. Keep source provenance for every imported record.
4. Treat 2026 as a real end year only when a source explicitly supports it.
5. Image imports must retain source URL, author and license.
6. API keys belong only in CI secrets.

The migration file in `supabase/migrations/` creates provenance tables. The importer is intentionally dry-run by default; enable writes only in a controlled CI job after reviewing the generated report.
