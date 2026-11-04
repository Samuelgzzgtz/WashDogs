-- Additive migration: dirección del cliente y si la mascota es nerviosa.
-- Solo agrega columnas con valor por defecto: conserva todos los registros y no afecta a la app Android,
-- que no envía ni lee estas columnas. Requiere 20261023000000_webapp.sql aplicada antes.
begin;

alter table public.clients add column if not exists address text not null default '';
alter table public.clients add column if not exists nervous boolean not null default false;

commit;
