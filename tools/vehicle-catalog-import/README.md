# Vehicle catalog import pipeline

The production Supabase project now has an authenticated Edge Function named `import-vehicle-catalog`.

It stages vPIC matches in `vehicle_catalog_source_refs` rather than marking them verified. vPIC is a US-oriented reference source, so cross-market validation is required before publishing Algerian/European vehicle data.

Do not write unverified source data directly into production vehicle generations or engines.
