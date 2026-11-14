-- Historial de conversación del bot de WhatsApp: para que recuerde de qué se
-- está hablando con cada número y para no contestar dos veces el mismo mensaje
-- (Meta reintenta). Aditiva. Requiere 20261114000000_bot.sql.
begin;

create table if not exists public.bot_messages (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  phone text not null,
  role text not null check (role in ('user', 'assistant')),
  text text not null,
  created_at timestamptz not null default now()
);

alter table public.bot_messages enable row level security;
do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'bot_messages' and policyname = 'washdog_owner_guard') then
    create policy washdog_owner_guard on public.bot_messages as restrictive for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
  end if;
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'bot_messages' and policyname = 'washdog_owner_access') then
    create policy washdog_owner_access on public.bot_messages for all to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);
  end if;
end $$;
grant select, insert, update, delete on public.bot_messages to authenticated;
revoke all on public.bot_messages from anon;
grant select, insert on public.bot_messages to service_role;

create index if not exists washdog_bot_messages_phone_idx on public.bot_messages(user_id, phone, created_at desc);

commit;
