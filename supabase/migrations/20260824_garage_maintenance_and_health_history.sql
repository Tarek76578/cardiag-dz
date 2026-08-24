create table if not exists public.vehicle_service_records (
 id uuid primary key default gen_random_uuid(), user_vehicle_id uuid not null references public.user_vehicles(id) on delete cascade,
 service_type text not null, title text not null, description text, mileage integer, cost numeric, currency text default 'DZD', performed_at date not null default current_date,
 parts jsonb not null default '[]'::jsonb, notes text, created_at timestamptz not null default now()
);
create table if not exists public.vehicle_reminders (
 id uuid primary key default gen_random_uuid(), user_vehicle_id uuid not null references public.user_vehicles(id) on delete cascade,
 reminder_type text not null, title text not null, due_date date, due_mileage integer, completed boolean not null default false, notes text, created_at timestamptz not null default now()
);
create table if not exists public.vehicle_notes (
 id uuid primary key default gen_random_uuid(), user_vehicle_id uuid not null references public.user_vehicles(id) on delete cascade,
 note text not null, created_at timestamptz not null default now(), updated_at timestamptz not null default now()
);
create index if not exists idx_service_vehicle_date on public.vehicle_service_records(user_vehicle_id,performed_at desc);
create index if not exists idx_reminders_vehicle_due on public.vehicle_reminders(user_vehicle_id,completed,due_date,due_mileage);
create index if not exists idx_vehicle_notes_vehicle on public.vehicle_notes(user_vehicle_id,created_at desc);
alter table public.vehicle_service_records enable row level security;
alter table public.vehicle_reminders enable row level security;
alter table public.vehicle_notes enable row level security;
drop policy if exists vehicle_service_owner on public.vehicle_service_records;
create policy vehicle_service_owner on public.vehicle_service_records for all to authenticated using (exists(select 1 from public.user_vehicles v where v.id=user_vehicle_id and v.user_id=auth.uid())) with check (exists(select 1 from public.user_vehicles v where v.id=user_vehicle_id and v.user_id=auth.uid()));
drop policy if exists vehicle_reminders_owner on public.vehicle_reminders;
create policy vehicle_reminders_owner on public.vehicle_reminders for all to authenticated using (exists(select 1 from public.user_vehicles v where v.id=user_vehicle_id and v.user_id=auth.uid())) with check (exists(select 1 from public.user_vehicles v where v.id=user_vehicle_id and v.user_id=auth.uid()));
drop policy if exists vehicle_notes_owner on public.vehicle_notes;
create policy vehicle_notes_owner on public.vehicle_notes for all to authenticated using (exists(select 1 from public.user_vehicles v where v.id=user_vehicle_id and v.user_id=auth.uid())) with check (exists(select 1 from public.user_vehicles v where v.id=user_vehicle_id and v.user_id=auth.uid()));
create index if not exists idx_user_vehicles_user_primary on public.user_vehicles(user_id,is_primary);
