-- Permisos mínimos para el servidor del bot (llave secreta / service_role):
-- leer clientes, citas y configuración; crear y actualizar avisos; limpiar
-- dispositivos de notificaciones que ya no existen. No toca datos.
begin;
grant select on public.clients, public.appointments, public.bot_config to service_role;
grant select, insert, update on public.bot_alerts to service_role;
grant select, delete on public.push_subscriptions to service_role;
commit;
