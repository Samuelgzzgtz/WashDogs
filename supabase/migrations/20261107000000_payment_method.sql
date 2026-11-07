-- Additive migration: método de pago al ingresar dinero.
-- Solo agrega una columna nullable: conserva todos los registros y no afecta
-- a la app Android ni a la web viejas, que no envían ni leen esta columna.
-- Valores: 'CASH' (Efectivo) y 'TRANSFER' (Transferencia; la tarjeta se
-- cuenta como transferencia, no es una tercera opción).
-- Alcance: obligatorio solo en ingresos IN nuevos. Gastos OUT quedan NULL
-- por ahora. Las filas viejas quedan NULL y se muestran como "Sin método".
-- No se convierte el historial en masa a efectivo. Requiere
-- 20261023000000_webapp.sql aplicada antes.
begin;

alter table public.transactions
  add column if not exists payment_method text
  check (payment_method is null or payment_method in ('CASH', 'TRANSFER'));

create index if not exists washdog_transactions_user_payment_idx
  on public.transactions(user_id, payment_method, date);

commit;
