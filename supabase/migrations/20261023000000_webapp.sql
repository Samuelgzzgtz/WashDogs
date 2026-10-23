-- Additive migration for the PWA. Existing Android accounts and records are retained.
begin;

create table if not exists public.clients (
  id text primary key, user_id uuid not null references auth.users(id) on delete cascade,
  owner_name text not null, pet_name text not null, breed text not null default '',
  last_visit text not null default '', needs_reschedule boolean not null default false,
  pet_type text not null default 'DOG', phone text not null default ''
);
create table if not exists public.appointments (
  id text primary key, user_id uuid not null references auth.users(id) on delete cascade,
  client_name text not null, pet_name text not null, service text not null default '',
  time text not null default '', date text not null, status text not null default 'SCHEDULED',
  notes text not null default ''
);
create table if not exists public.transactions (
  id text primary key, user_id uuid not null references auth.users(id) on delete cascade,
  type text not null, amount double precision not null, concept text not null default '',
  detail text not null default '', date text not null
);
create table if not exists public.user_settings (
  user_id uuid primary key references auth.users(id) on delete cascade,
  tpl_welcome text not null, tpl_confirm text not null, tpl_reschedule text not null
);
create table if not exists public.tombstones (
  user_id uuid not null references auth.users(id) on delete cascade,
  entity text not null, entity_id text not null, deleted_at text not null,
  primary key (user_id, entity, entity_id)
);

alter table public.clients add column if not exists updated_at text not null default '';
alter table public.appointments add column if not exists updated_at text not null default '';
alter table public.appointments add column if not exists client_id text not null default '';
alter table public.appointments add column if not exists end_time text not null default '';
alter table public.appointments add column if not exists repeat_every_days integer;
alter table public.appointments add column if not exists series_id text;
alter table public.transactions add column if not exists updated_at text not null default '';
alter table public.user_settings add column if not exists updated_at text not null default '';

-- Keep the recurrence rule independent from individual occurrences that can be moved or edited.
create table if not exists public.appointment_series (
  id text primary key, user_id uuid not null references auth.users(id) on delete cascade,
  template jsonb not null, stopped_at text not null default ''
);
insert into public.appointment_series(id, user_id, template)
select distinct on (user_id, series_id) series_id, user_id,
  to_jsonb(a) || jsonb_build_object('date', case
    when id like series_id || '@%' then split_part(id, '@', 2) else date end)
from public.appointments a where series_id is not null and repeat_every_days > 0
  and not exists (select 1 from public.tombstones t where t.user_id = a.user_id
    and t.entity = 'appointment_series' and t.entity_id = a.series_id)
  and exists (select 1 from public.clients c where c.user_id = a.user_id
    and (c.id = a.client_id or (a.client_id = '' and c.owner_name = a.client_name and c.pet_name = a.pet_name)))
  and not exists (select 1 from public.tombstones t where t.user_id = a.user_id and t.entity = 'appointments'
    and t.entity_id = a.id and t.deleted_at::timestamptz >= coalesce(nullif(a.updated_at, '')::timestamptz, '-infinity'::timestamptz))
  and not exists (select 1 from public.tombstones t where t.user_id = a.user_id and t.entity = 'clients'
    and (t.entity_id = a.client_id or (a.client_id = '' and exists (
      select 1 from public.clients c where c.user_id = a.user_id and c.id = t.entity_id
        and c.owner_name = a.client_name and c.pet_name = a.pet_name)))
    and not exists (select 1 from public.clients c where c.user_id = a.user_id and c.id = t.entity_id
      and coalesce(nullif(c.updated_at, '')::timestamptz, '-infinity'::timestamptz) > t.deleted_at::timestamptz))
order by user_id, series_id, (id like series_id || '@%') desc, date
on conflict (id) do nothing;

do $$
declare target text;
begin
  foreach target in array array['clients', 'appointments', 'transactions', 'user_settings', 'tombstones', 'appointment_series'] loop
    execute format('alter table public.%I enable row level security', target);
    -- A restrictive ownership policy also protects installations with older permissive policies.
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

create index if not exists washdog_clients_user_idx on public.clients(user_id);
create index if not exists washdog_appointments_user_date_idx on public.appointments(user_id, date);
create index if not exists washdog_transactions_user_date_idx on public.transactions(user_id, date);

-- Trigger functions run with the caller's permissions. RLS remains active for every write.
create or replace function public.washdog_record_deletion() returns trigger
language plpgsql set search_path = '' as $$
begin
  insert into public.tombstones(user_id, entity, entity_id, deleted_at)
  values (old.user_id, tg_table_name, old.id, to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'))
  on conflict (user_id, entity, entity_id) do update set deleted_at = excluded.deleted_at;
  return old;
exception when foreign_key_violation then
  -- auth.users ON DELETE CASCADE has already removed the account. No tombstone survives it.
  return old;
end $$;

create or replace function public.washdog_keep_latest_deletion() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.deleted_at::timestamptz < old.deleted_at::timestamptz then
    new.deleted_at = old.deleted_at;
  end if;
  return new;
end $$;

create or replace function public.washdog_ignore_deleted_write() returns trigger
language plpgsql set search_path = '' as $$
declare current_updated_at text;
begin
  if tg_table_name = 'user_settings' then
    select updated_at into current_updated_at from public.user_settings where user_id = new.user_id;
  else
    if exists (select 1 from public.tombstones t where t.user_id = new.user_id
      and t.entity = tg_table_name and t.entity_id = new.id
      and t.deleted_at::timestamptz >= coalesce(nullif(new.updated_at, '')::timestamptz, '-infinity'::timestamptz)) then
      return null;
    end if;
    execute format('select updated_at from public.%I where id = $1 and user_id = $2', tg_table_name)
      into current_updated_at using new.id, new.user_id;
  end if;
  if coalesce(nullif(new.updated_at, '')::timestamptz, '-infinity'::timestamptz)
      < coalesce(nullif(current_updated_at, '')::timestamptz, '-infinity'::timestamptz) then return null; end if;
  return new;
end $$;

create or replace function public.washdog_link_renamed_client() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.owner_name is distinct from old.owner_name or new.pet_name is distinct from old.pet_name then
    update public.appointment_series set template = template || jsonb_build_object('client_id', old.id, 'client_name', new.owner_name, 'pet_name', new.pet_name)
    where user_id = old.user_id and (template->>'client_id' = old.id or
      (coalesce(template->>'client_id', '') = '' and template->>'client_name' = old.owner_name and template->>'pet_name' = old.pet_name));
    update public.appointments set client_id = old.id, client_name = new.owner_name, pet_name = new.pet_name,
      updated_at = to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
    where user_id = old.user_id and (client_id = old.id or
      (client_id = '' and client_name = old.owner_name and pet_name = old.pet_name))
      and not exists (select 1 from public.tombstones t where t.user_id = appointments.user_id
        and t.entity = 'appointments' and t.entity_id = appointments.id
        and t.deleted_at::timestamptz >= coalesce(nullif(appointments.updated_at, '')::timestamptz, '-infinity'::timestamptz));
  end if;
  return new;
end $$;

create or replace function public.washdog_track_series() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.series_id is null or new.repeat_every_days is null then return new; end if;
  perform pg_advisory_xact_lock(hashtextextended(new.user_id::text, 0));
  if exists (select 1 from public.tombstones where user_id = new.user_id
    and entity = 'appointment_series' and entity_id = new.series_id) then return null; end if;
  if exists (select 1 from public.appointment_series where user_id = new.user_id and id = new.series_id and stopped_at <> '') then
    return null;
  end if;
  insert into public.appointment_series(id, user_id, template) values (new.series_id, new.user_id, to_jsonb(new))
    on conflict (id) do nothing;
  return new;
end $$;

create or replace function public.washdog_cascade_client() returns trigger
language plpgsql set search_path = '' as $$
begin
  perform pg_advisory_xact_lock(hashtextextended(old.user_id::text, 0));
  update public.appointment_series set stopped_at = to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
    where user_id = old.user_id and (template->>'client_id' = old.id or
      (coalesce(template->>'client_id', '') = '' and template->>'client_name' = old.owner_name and template->>'pet_name' = old.pet_name));
  delete from public.appointments where user_id = old.user_id and
    (client_id = old.id or (client_id = '' and client_name = old.owner_name and pet_name = old.pet_name));
  return old;
end $$;

create or replace function public.washdog_update_visit() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.status = 'DONE' then
    update public.clients set last_visit = greatest(last_visit, new.date), needs_reschedule = false,
      updated_at = to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
    where user_id = new.user_id and (last_visit < new.date or needs_reschedule) and (id = new.client_id or
      (new.client_id = '' and owner_name = new.client_name and pet_name = new.pet_name));
  end if;
  return new;
end $$;

-- Serialize booking checks per account so two devices cannot book the same time.
create or replace function public.washdog_check_overlap() returns trigger
language plpgsql set search_path = '' as $$
declare start_at time; finish_at time;
begin
  -- Upsert fires BEFORE INSERT even for an existing row. Unchanged slots remain editable.
  if exists (select 1 from public.appointments a where a.user_id = new.user_id and a.id = new.id
      and a.date = new.date and a.time = new.time and a.end_time = new.end_time) then return new; end if;
  if tg_op = 'UPDATE' and new.date = old.date and new.time = old.time and new.end_time = old.end_time then
    return new;
  end if;
  if new.time = '' then return new; end if;
  start_at := new.time::time;
  finish_at := coalesce(nullif(new.end_time, '')::time, start_at + interval '1 hour');
  if finish_at <= start_at then raise exception 'WASHDOG_INVALID_TIME'; end if;
  perform pg_advisory_xact_lock(hashtextextended(new.user_id::text, 0));
  if exists (
    select 1 from public.appointments a where a.user_id = new.user_id and a.date = new.date
      and a.id <> new.id and a.time <> '' and start_at < coalesce(nullif(a.end_time, '')::time, a.time::time + interval '1 hour')
      and finish_at > a.time::time
      and not exists (select 1 from public.tombstones t where t.user_id = a.user_id and t.entity = 'appointments'
        and t.entity_id = a.id and t.deleted_at::timestamptz >= coalesce(nullif(a.updated_at, '')::timestamptz, '-infinity'::timestamptz))
  ) then raise exception 'WASHDOG_OVERLAP'; end if;
  return new;
end $$;

-- Alphabetical trigger order makes stale Android uploads stop before booking/visit side effects.
drop trigger if exists washdog_00_stale on public.clients;
create trigger washdog_00_stale before insert or update on public.clients for each row execute function public.washdog_ignore_deleted_write();
drop trigger if exists washdog_00_stale on public.appointments;
create trigger washdog_00_stale before insert or update on public.appointments for each row execute function public.washdog_ignore_deleted_write();
drop trigger if exists washdog_00_stale on public.transactions;
create trigger washdog_00_stale before insert or update on public.transactions for each row execute function public.washdog_ignore_deleted_write();
drop trigger if exists washdog_00_stale on public.user_settings;
create trigger washdog_00_stale before insert or update on public.user_settings for each row execute function public.washdog_ignore_deleted_write();
drop trigger if exists washdog_01_series on public.appointments;
create trigger washdog_01_series before insert or update on public.appointments for each row execute function public.washdog_track_series();
drop trigger if exists washdog_tombstone_latest on public.tombstones;
create trigger washdog_tombstone_latest before update on public.tombstones for each row execute function public.washdog_keep_latest_deletion();
drop trigger if exists washdog_client_rename on public.clients;
create trigger washdog_client_rename before update of owner_name, pet_name on public.clients for each row execute function public.washdog_link_renamed_client();

drop trigger if exists washdog_client_cascade on public.clients;
create trigger washdog_client_cascade before delete on public.clients for each row execute function public.washdog_cascade_client();
drop trigger if exists washdog_client_deleted on public.clients;
create trigger washdog_client_deleted after delete on public.clients for each row execute function public.washdog_record_deletion();
drop trigger if exists washdog_appointment_deleted on public.appointments;
create trigger washdog_appointment_deleted after delete on public.appointments for each row execute function public.washdog_record_deletion();
drop trigger if exists washdog_transaction_deleted on public.transactions;
create trigger washdog_transaction_deleted after delete on public.transactions for each row execute function public.washdog_record_deletion();
drop trigger if exists washdog_visit_done on public.appointments;
create trigger washdog_visit_done after insert or update of status on public.appointments for each row execute function public.washdog_update_visit();
drop trigger if exists washdog_overlap on public.appointments;
create trigger washdog_overlap before insert or update of date, time, end_time on public.appointments for each row execute function public.washdog_check_overlap();

create or replace function public.washdog_stop_series(p_series_id text, p_today text) returns void
language plpgsql security invoker set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  perform pg_advisory_xact_lock(hashtextextended(auth.uid()::text, 0));
  update public.appointment_series set stopped_at = to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
    where user_id = auth.uid() and id = p_series_id and stopped_at = '';
  -- Disable historical occurrences too, otherwise Android would top up the series again.
  update public.appointments set repeat_every_days = null,
    updated_at = to_char(clock_timestamp() at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"')
    where user_id = auth.uid() and series_id = p_series_id and repeat_every_days is not null;
  delete from public.appointments where user_id = auth.uid() and series_id = p_series_id
    and date >= p_today and status <> 'DONE';
end $$;
revoke all on function public.washdog_stop_series(text, text) from public;
grant execute on function public.washdog_stop_series(text, text) to authenticated;

commit;
