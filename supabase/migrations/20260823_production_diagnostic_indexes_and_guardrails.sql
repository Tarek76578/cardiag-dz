create index if not exists idx_vehicle_models_make_name on public.vehicle_models(make_id, name);
create index if not exists idx_vehicle_models_search_text on public.vehicle_models using gin(to_tsvector('simple', search_text));
create index if not exists idx_vehicle_images_model_primary on public.vehicle_images(model_id, is_primary, sort_order);
create index if not exists idx_user_vehicles_user_updated on public.user_vehicles(user_id, updated_at desc);
create index if not exists idx_diagnostic_sessions_user_created on public.diagnostic_sessions(user_id, created_at desc);
create index if not exists idx_diagnostic_sessions_vehicle_created on public.diagnostic_sessions(user_vehicle_id, created_at desc);
create index if not exists idx_diagnostic_results_session_created on public.diagnostic_results(session_id, created_at desc);
create index if not exists idx_diagnostic_measurements_session_captured on public.diagnostic_measurements(session_id, captured_at desc);
create index if not exists idx_diagnostic_codes_code on public.diagnostic_codes(code);
create index if not exists idx_diagnostic_codes_system_category on public.diagnostic_codes(system, category);

alter table public.diagnostic_sessions drop constraint if exists diagnostic_sessions_mileage_check;
alter table public.diagnostic_sessions add constraint diagnostic_sessions_mileage_check check (mileage is null or mileage >= 0);
alter table public.user_vehicles drop constraint if exists user_vehicles_mileage_check;
alter table public.user_vehicles add constraint user_vehicles_mileage_check check (mileage is null or mileage >= 0);
alter table public.user_vehicles drop constraint if exists user_vehicles_year_check;
alter table public.user_vehicles add constraint user_vehicles_year_check check (year is null or year between 1886 and extract(year from now())::int + 2);
alter table public.diagnostic_sessions drop constraint if exists diagnostic_sessions_language_check;
alter table public.diagnostic_sessions add constraint diagnostic_sessions_language_check check (language in ('ar','fr'));

alter table public.diagnostic_sessions enable row level security;
alter table public.diagnostic_results enable row level security;
alter table public.diagnostic_measurements enable row level security;
alter table public.diagnostic_session_faults enable row level security;
alter table public.diagnostic_session_symptoms enable row level security;
alter table public.user_vehicles enable row level security;
