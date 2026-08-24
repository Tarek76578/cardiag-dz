create table if not exists public.vehicle_catalog_sources (
  id uuid primary key default gen_random_uuid(),
  source_key text not null unique,
  source_name text not null,
  base_url text,
  role text,
  commercial_use text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.vehicle_catalog_source_refs (
  id uuid primary key default gen_random_uuid(),
  source_id uuid not null references public.vehicle_catalog_sources(id) on delete cascade,
  model_id uuid references public.vehicle_models(id) on delete cascade,
  generation_id uuid references public.vehicle_generations(id) on delete cascade,
  external_id text,
  source_url text,
  raw_year_from smallint,
  raw_year_to smallint,
  raw_name text,
  confidence numeric(5,4),
  verified boolean not null default false,
  raw_payload jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(source_id, model_id, generation_id, external_id)
);

create index if not exists idx_vehicle_catalog_refs_model on public.vehicle_catalog_source_refs(model_id);
create index if not exists idx_vehicle_catalog_refs_generation on public.vehicle_catalog_source_refs(generation_id);
create index if not exists idx_vehicle_catalog_refs_source on public.vehicle_catalog_source_refs(source_id);

insert into public.vehicle_catalog_sources(source_key, source_name, base_url, role, commercial_use) values
('vpic','NHTSA vPIC','https://vpic.nhtsa.dot.gov/api/','vehicle identity and VIN-oriented reference data','free/public API; verify current terms before redistribution'),
('wikimedia','Wikimedia Commons','https://commons.wikimedia.org/w/api.php','vehicle images with per-file attribution and license metadata','license-dependent'),
('api_ninjas','API Ninjas Cars','https://api.api-ninjas.com/v1/cars','optional commercial enrichment for specifications','paid plan / contract dependent')
on conflict (source_key) do update set source_name=excluded.source_name, base_url=excluded.base_url, role=excluded.role, commercial_use=excluded.commercial_use, updated_at=now();
