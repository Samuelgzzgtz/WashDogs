-- Additive migration: base del bot de WhatsApp (secretario).
--  * clients.size / clients.coat: talla y tipo de pelo, para calcular cuánto dura cada servicio.
--  * bot_config: precios, duraciones, zonas y textos del bot, editables desde la app (una fila por cuenta).
--  * bot_alerts: avisos que el bot deja para que una persona decida (citas pedidas, mismo día, fuera de zona…).
--  * push_subscriptions: dispositivos donde la app WashDog muestra notificaciones de los avisos.
-- Solo agrega columnas con valor por defecto y tablas nuevas: no toca datos existentes ni la app Android.
-- Requiere 20261023000000_webapp.sql aplicada antes.
begin;

alter table public.clients add column if not exists size text not null default '';
alter table public.clients add column if not exists coat text not null default '';
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'clients_size_check') then
    alter table public.clients add constraint clients_size_check
      check (size in ('', 'PEQUENO', 'MEDIANO', 'GRANDE', 'JUMBO'));
  end if;
  if not exists (select 1 from pg_constraint where conname = 'clients_coat_check') then
    alter table public.clients add constraint clients_coat_check check (coat in ('', 'CORTO', 'LARGO'));
  end if;
end $$;

create table if not exists public.bot_config (
  user_id uuid primary key references auth.users(id) on delete cascade,
  config jsonb not null default '{}'::jsonb check (jsonb_typeof(config) = 'object'),
  updated_at text not null default ''
);

create table if not exists public.bot_alerts (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('BOOKING', 'SAME_DAY', 'OUT_OF_ZONE', 'REVIEW', 'TEST')),
  phone text not null default '',
  customer_name text not null default '',
  summary text not null check (length(btrim(summary)) between 1 and 2000),
  requested_date text not null default '',
  requested_time text not null default '',
  status text not null default 'PENDING' check (status in ('PENDING', 'ACCEPTED', 'REJECTED')),
  created_at timestamptz not null default now(),
  updated_at text not null default ''
);

create table if not exists public.push_subscriptions (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  endpoint text not null unique,
  p256dh text not null,
  auth text not null,
  user_agent text not null default '',
  created_at timestamptz not null default now()
);

do $$
declare target text;
begin
  foreach target in array array['bot_config', 'bot_alerts', 'push_subscriptions'] loop
    execute format('alter table public.%I enable row level security', target);
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = target and policyname = 'washdog_owner_guard') then
      execute format('create policy washdog_owner_guard on public.%I as restrictive for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id)', target);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = target and policyname = 'washdog_owner_access') then
      execute format('create policy washdog_owner_access on public.%I for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id)', target);
    end if;
    execute format('grant select, insert, update, delete on public.%I to authenticated', target);
    execute format('revoke all on public.%I from anon', target);
  end loop;
end $$;

create index if not exists washdog_bot_alerts_user_idx on public.bot_alerts(user_id, created_at desc);
create index if not exists washdog_push_subscriptions_user_idx on public.push_subscriptions(user_id);

commit;
