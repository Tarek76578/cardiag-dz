-- Keep reporting/audit views subject to the caller's RLS policies.
alter view public.vehicle_profile_coverage set (security_invoker = true);
alter view public.vehicle_catalog_quality_audit set (security_invoker = true);
alter view public.vehicle_generation_specificity_audit set (security_invoker = true);
alter view public.vehicle_year_quality_audit set (security_invoker = true);
alter view public.vehicle_year_engine_catalog set (security_invoker = true);
alter view public.vehicle_data_coverage_summary set (security_invoker = true);
alter view public.vehicle_catalog_coverage set (security_invoker = true);
alter view public.vehicle_year_full_coverage set (security_invoker = true);
