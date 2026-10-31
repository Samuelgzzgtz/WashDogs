-- Additive migration: Mexican peso coin counter (one row per user and denomination).
-- Same conventions as 20261023000000_webapp.sql: text id, ISO text updated_at, RLS by auth.uid(),
-- shared stale-write and tombstone triggers. Requires that migration to be applied first.
begin;

create table if not exists public.coin_counts (
  id text primary key, user_id uuid not null references auth.users(id) on delete cascade,
  denomination numeric(5, 2) not null check (denomination in (0.50, 1, 2, 5, 10, 20)),
  quantity integer not null default 0 check (quantity >= 0),
  updated_at text not null default '',
  unique (user_id, denomination)
);

alter table public.coin_counts enable row level security;
-- A restrictive ownership policy also protects installations with older permissive policies.
do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'coin_counts' and policyname = 'washdog_owner_guard') then
    create policy washdog_owner_guard on public.coin_counts as restrictive for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'coin_counts' and policyname = 'washdog_owner_access') then
    create policy washdog_owner_access on public.coin_counts for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
  end if;
end $$;
grant select, insert, update, delete on public.coin_counts to authenticated;
revoke all on public.coin_counts from anon;

-- Stale writes are ignored and deletions leave a tombstone, like every other synced table.
drop trigger if exists washdog_00_stale on public.coin_counts;
create trigger washdog_00_stale before insert or update on public.coin_counts for each row execute function public.washdog_ignore_deleted_write();
drop trigger if exists washdog_coin_count_deleted on public.coin_counts;
create trigger washdog_coin_count_deleted after delete on public.coin_counts for each row execute function public.washdog_record_deletion();

commit;
