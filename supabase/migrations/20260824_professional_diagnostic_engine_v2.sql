create table if not exists public.diagnostic_ecus (
 id uuid primary key default gen_random_uuid(),
 session_id uuid not null references public.diagnostic_sessions(id) on delete cascade,
 ecu_name text not null, ecu_address text, protocol text,
 status text not null default 'unknown', response_ms integer, error text,
 created_at timestamptz not null default now()
);
create index if not exists idx_diagnostic_ecus_session on public.diagnostic_ecus(session_id);
create table if not exists public.freeze_frames (
 id uuid primary key default gen_random_uuid(), session_id uuid not null references public.diagnostic_sessions(id) on delete cascade,
 dtc_code text, data jsonb not null default '{}'::jsonb, raw_response text, captured_at timestamptz not null default now()
);
create index if not exists idx_freeze_frames_session on public.freeze_frames(session_id);
create table if not exists public.diagnostic_pid_readings (
 id uuid primary key default gen_random_uuid(), session_id uuid not null references public.diagnostic_sessions(id) on delete cascade,
 pid text not null, name text, value_numeric numeric, unit text, raw_response text, sampled_at timestamptz not null default now()
);
create index if not exists idx_pid_readings_session_time on public.diagnostic_pid_readings(session_id,sampled_at desc);
create table if not exists public.diagnostic_reports (
 id uuid primary key default gen_random_uuid(), session_id uuid not null references public.diagnostic_sessions(id) on delete cascade,
 report_type text not null default 'diagnostic', title text, payload jsonb not null default '{}'::jsonb, created_at timestamptz not null default now()
);
create index if not exists idx_diagnostic_reports_session on public.diagnostic_reports(session_id);
create table if not exists public.vehicle_health_scores (
 id uuid primary key default gen_random_uuid(), session_id uuid not null references public.diagnostic_sessions(id) on delete cascade,
 overall integer, engine integer, transmission integer, emissions integer, electrical integer, obd integer,
 factors jsonb not null default '{}'::jsonb, created_at timestamptz not null default now(),
 check (overall is null or overall between 0 and 100), check (engine is null or engine between 0 and 100),
 check (transmission is null or transmission between 0 and 100), check (emissions is null or emissions between 0 and 100),
 check (electrical is null or electrical between 0 and 100), check (obd is null or obd between 0 and 100)
);
create index if not exists idx_health_scores_session on public.vehicle_health_scores(session_id,created_at desc);
alter table public.diagnostic_sessions add column if not exists adapter_name text;
alter table public.diagnostic_sessions add column if not exists adapter_protocol text;
alter table public.diagnostic_sessions add column if not exists scan_type text;
alter table public.diagnostic_sessions add column if not exists duration_ms integer;
alter table public.diagnostic_sessions add column if not exists completed_at timestamptz;
alter table public.diagnostic_results add column if not exists confidence numeric;
alter table public.diagnostic_results add column if not exists model_version text;
alter table public.diagnostic_results add column if not exists safety_notes jsonb not null default '[]'::jsonb;
alter table public.diagnostic_ecus enable row level security;
alter table public.freeze_frames enable row level security;
alter table public.diagnostic_pid_readings enable row level security;
alter table public.diagnostic_reports enable row level security;
alter table public.vehicle_health_scores enable row level security;
drop policy if exists diagnostic_ecus_owner on public.diagnostic_ecus;
create policy diagnostic_ecus_owner on public.diagnostic_ecus for all to authenticated using (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid())) with check (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid()));
drop policy if exists freeze_frames_owner on public.freeze_frames;
create policy freeze_frames_owner on public.freeze_frames for all to authenticated using (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid())) with check (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid()));
drop policy if exists diagnostic_pid_readings_owner on public.diagnostic_pid_readings;
create policy diagnostic_pid_readings_owner on public.diagnostic_pid_readings for all to authenticated using (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid())) with check (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid()));
drop policy if exists diagnostic_reports_owner on public.diagnostic_reports;
create policy diagnostic_reports_owner on public.diagnostic_reports for all to authenticated using (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid())) with check (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid()));
drop policy if exists vehicle_health_scores_owner on public.vehicle_health_scores;
create policy vehicle_health_scores_owner on public.vehicle_health_scores for all to authenticated using (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid())) with check (exists(select 1 from public.diagnostic_sessions s where s.id=session_id and s.user_id=auth.uid()));
create index if not exists idx_diagnostic_results_session on public.diagnostic_results(session_id,created_at desc);
create index if not exists idx_diagnostic_sessions_user on public.diagnostic_sessions(user_id,created_at desc);
create index if not exists idx_diagnostic_codes_code on public.diagnostic_codes(code);
create index if not exists idx_code_vehicle_model on public.diagnostic_code_vehicles(model_id,code_id);
create index if not exists idx_code_vehicle_generation on public.diagnostic_code_vehicles(generation_id,code_id);
create index if not exists idx_vehicle_images_model_primary on public.vehicle_images(model_id,is_primary,sort_order);
create index if not exists idx_vehicle_engines_generation on public.vehicle_engines(generation_id);
create index if not exists idx_vehicle_ecus_generation_engine on public.vehicle_ecus(generation_id,engine_id);
create index if not exists idx_vehicle_specs_generation_engine on public.vehicle_specifications(generation_id,engine_id);
