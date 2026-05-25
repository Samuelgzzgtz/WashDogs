-- WashDogs — migración completa (drop + create + RLS)
-- Pegar en: supabase.com/dashboard/project/ristcoimdzucocvttefs/sql/new

-- ── 1. Limpiar ────────────────────────────────────────────────────────────────
drop table if exists user_settings cascade;
drop table if exists transactions   cascade;
drop table if exists appointments   cascade;
drop table if exists clients        cascade;

-- ── 2. clients ────────────────────────────────────────────────────────────────
create table clients (
  id               text    primary key,
  user_id          uuid    not null references auth.users(id) on delete cascade,
  owner_name       text    not null,
  pet_name         text    not null,
  breed            text    not null default '',
  last_visit       text    not null,
  needs_reschedule boolean not null default false,
  pet_type         text    not null default 'DOG',
  phone            text    not null default ''
);
alter table clients enable row level security;
create policy "clients_select" on clients for select to authenticated using      (auth.uid() = user_id);
create policy "clients_insert" on clients for insert to authenticated with check (auth.uid() = user_id);
create policy "clients_update" on clients for update to authenticated using      (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "clients_delete" on clients for delete to authenticated using      (auth.uid() = user_id);

-- ── 3. appointments ───────────────────────────────────────────────────────────
create table appointments (
  id          text primary key,
  user_id     uuid not null references auth.users(id) on delete cascade,
  client_name text not null,
  pet_name    text not null,
  service     text not null default '',
  time        text not null default '',
  date        text not null,
  status      text not null default 'SCHEDULED',
  notes       text not null default ''
);
alter table appointments enable row level security;
create policy "appointments_select" on appointments for select to authenticated using      (auth.uid() = user_id);
create policy "appointments_insert" on appointments for insert to authenticated with check (auth.uid() = user_id);
create policy "appointments_update" on appointments for update to authenticated using      (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "appointments_delete" on appointments for delete to authenticated using      (auth.uid() = user_id);

-- ── 4. transactions ───────────────────────────────────────────────────────────
create table transactions (
  id      text             primary key,
  user_id uuid             not null references auth.users(id) on delete cascade,
  type    text             not null,
  amount  double precision not null,
  concept text             not null default '',
  detail  text             not null default '',
  date    text             not null
);
alter table transactions enable row level security;
create policy "transactions_select" on transactions for select to authenticated using      (auth.uid() = user_id);
create policy "transactions_insert" on transactions for insert to authenticated with check (auth.uid() = user_id);
create policy "transactions_update" on transactions for update to authenticated using      (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "transactions_delete" on transactions for delete to authenticated using      (auth.uid() = user_id);

-- ── 5. user_settings ─────────────────────────────────────────────────────────
create table user_settings (
  user_id        uuid primary key references auth.users(id) on delete cascade,
  tpl_welcome    text not null default '',
  tpl_confirm    text not null default '',
  tpl_reschedule text not null default ''
);
alter table user_settings enable row level security;
create policy "settings_select" on user_settings for select to authenticated using      (auth.uid() = user_id);
create policy "settings_insert" on user_settings for insert to authenticated with check (auth.uid() = user_id);
create policy "settings_update" on user_settings for update to authenticated using      (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "settings_delete" on user_settings for delete to authenticated using      (auth.uid() = user_id);
