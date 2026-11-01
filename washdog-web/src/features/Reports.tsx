import { useState } from 'react'
import { ArrowDownLeft, ArrowUpRight, Download, Trash2 } from 'lucide-react'
import { Empty, Field, Modal, MoneyInput } from '../components'
import { deleteRow, errorMessage, saveRow, useSave } from '../data/useData'
import type { Snapshot, Transaction } from '../data/types'
import {
  accountBalance,
  isoDate,
  money,
  monthClosing,
  monthClosings,
  openingBalance,
  readableDate,
  services,
  totals,
  transactionsCsv,
} from '../domain'

const monthLabel = (month: string) =>
  new Date(`${month}-01T12:00:00`).toLocaleDateString('es-MX', {
    month: 'long',
    year: 'numeric',
  })

export function Balance({ data, month }: { data: Snapshot; month: string }) {
  const { income, expense } = totals(data.transactions, month)
  const opening = openingBalance(data.transactions, month)
  const monthBalance = accountBalance(data.transactions, month)
  const isCurrent = month === isoDate().slice(0, 7)
  return (
    <div className="balance">
      <div className="balance-main">
        <span>{isCurrent ? 'Caja total' : 'Caja al cierre del mes'}</span>
        <strong>{money(opening + monthBalance)}</strong>
        <small className="balance-note">
          Saldo anterior {money(opening)} + este mes {money(monthBalance)}
        </small>
        <small>MXN · {monthLabel(month)}</small>
      </div>
      <div className="balance-details">
        <div>
          <span>
            <ArrowDownLeft size={17} />
            Ingresos del mes
          </span>
          <strong>{money(income)}</strong>
        </div>
        <div>
          <span>
            <ArrowUpRight size={17} />
            Gastos del mes
          </span>
          <strong>{money(expense)}</strong>
        </div>
      </div>
    </div>
  )
}

function CashClose({
  data,
  month,
  select,
}: {
  data: Snapshot
  month: string
  select: (month: string) => void
}) {
  const current = isoDate().slice(0, 7)
  const close = monthClosing(data.transactions, month)
  const previous = monthClosings(data.transactions, current).filter(
    (item) => item.month < current,
  )
  return (
    <section className="cash-close" aria-labelledby="cash-close-title">
      <div className="section-heading">
        <h2 id="cash-close-title">Cierre de caja</h2>
        <small className="cash-close-state">
          {monthLabel(month)} ·{' '}
          {month === current
            ? 'corte al día de hoy'
            : month < current
              ? 'mes cerrado'
              : 'mes futuro'}
        </small>
      </div>
      <dl className="cash-close-sheet">
        <div>
          <dt>Saldo inicial</dt>
          <dd>{money(close.opening)}</dd>
        </div>
        <div>
          <dt>+ Ingresos</dt>
          <dd>{money(close.income)}</dd>
        </div>
        <div>
          <dt>− Gastos</dt>
          <dd>
            {money(close.expense)}
            {close.goals > 0 && (
              <small>incluye {money(close.goals)} en aportes a metas</small>
            )}
          </dd>
        </div>
        <div className="cash-close-total">
          <dt>= Saldo final</dt>
          <dd>{money(close.closing)}</dd>
        </div>
      </dl>
      {previous.length > 0 && (
        <>
          <h3 className="cash-close-sub">Cierres anteriores</h3>
          <div className="cash-close-list">
            {previous.map((item) => (
              <button
                type="button"
                key={item.month}
                aria-pressed={item.month === month}
                onClick={() => select(item.month)}
              >
                <span>{monthLabel(item.month)}</span>
                <small>
                  Ingresos {money(item.income)} · Gastos {money(item.expense)}
                </small>
                <strong>{money(item.closing)}</strong>
              </button>
            ))}
          </div>
        </>
      )}
    </section>
  )
}

export function Reports({ data, uid }: { data: Snapshot; uid: string }) {
  const [month, setMonth] = useState(isoDate().slice(0, 7))
  const [type, setType] = useState<'IN' | 'OUT' | null>(null)
  const [deleting, setDeleting] = useState<Transaction | null>(null)
  const save = useSave(uid)
  const transactions = data.transactions
    .filter((tx) => tx.date.startsWith(month))
    .sort((a, b) => b.date.localeCompare(a.date))
  function download() {
    const url = URL.createObjectURL(
      new Blob([transactionsCsv(transactions)], {
        type: 'text/csv;charset=utf-8',
      }),
    )
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `washdog-${month}.csv`
    anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Cuentas claras</h1>
          <p>Cada cuidado también hace crecer tu negocio.</p>
        </div>
        <input
          className="month-input"
          type="month"
          aria-label="Mes del reporte"
          value={month}
          onChange={(event) => {
            if (event.target.value) setMonth(event.target.value)
          }}
        />
      </div>
      <Balance data={data} month={month} />
      <CashClose data={data} month={month} select={setMonth} />
      <div className="money-actions">
        <button className="primary" onClick={() => setType('IN')}>
          <ArrowDownLeft size={18} />
          Registrar ingreso
        </button>
        <button className="secondary" onClick={() => setType('OUT')}>
          <ArrowUpRight size={18} />
          Registrar gasto
        </button>
      </div>
      <div className="section-heading">
        <h2>Movimientos</h2>
        <button
          className="text-button"
          disabled={!transactions.length}
          onClick={download}
        >
          <Download size={17} />
          Exportar CSV
        </button>
      </div>
      <div>
        {transactions.map((tx) => (
          <article className="transaction-row" key={tx.id}>
            <div
              className={`transaction-icon ${tx.type === 'OUT' ? 'expense' : ''}`}
            >
              {tx.type === 'IN' ? (
                <ArrowDownLeft size={20} />
              ) : (
                <ArrowUpRight size={20} />
              )}
            </div>
            <div className="transaction-info">
              <h3>{tx.concept}</h3>
              <p>{tx.detail}</p>
              <small>{readableDate(tx.date.slice(0, 10))}</small>
            </div>
            <strong className={tx.type === 'OUT' ? 'expense-text' : ''}>
              {tx.type === 'IN' ? '+' : '−'}
              {money(tx.amount)}
            </strong>
            <button
              className="icon-button danger"
              aria-label={`Eliminar ${tx.concept}`}
              onClick={() => {
                save.reset()
                setDeleting(tx)
              }}
            >
              <Trash2 size={17} />
            </button>
          </article>
        ))}
      </div>
      {!transactions.length && <Empty>Aún no hay movimientos este mes.</Empty>}
      {type && (
        <TransactionForm uid={uid} type={type} close={() => setType(null)} />
      )}
      {deleting && (
        <Modal
          title="¿Eliminar este movimiento?"
          close={() => setDeleting(null)}
          busy={save.isPending}
        >
          <p>
            {deleting.concept} · {money(deleting.amount)}
          </p>
          {save.error && (
            <p className="error" role="alert">
              {errorMessage(save.error)}
            </p>
          )}
          <div className="form-actions">
            <button
              className="secondary"
              disabled={save.isPending}
              onClick={() => setDeleting(null)}
            >
              Cancelar
            </button>
            <button
              className="primary destructive"
              disabled={save.isPending}
              onClick={() =>
                save.mutate(() => deleteRow('transactions', deleting.id, uid), {
                  onSuccess: () => setDeleting(null),
                })
              }
            >
              {save.isPending ? 'Eliminando…' : 'Eliminar movimiento'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

export function TransactionForm({
  uid,
  type,
  close,
}: {
  uid: string
  type: 'IN' | 'OUT'
  close: () => void
}) {
  const [amount, setAmount] = useState('')
  const [id] = useState(() => crypto.randomUUID())
  const save = useSave(uid)
  return (
    <Modal
      title={type === 'IN' ? 'Registrar ingreso' : 'Registrar gasto'}
      close={close}
      busy={save.isPending}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          const form = new FormData(event.currentTarget)
          const row: Transaction = {
            id,
            user_id: uid,
            type,
            amount: Math.round(Number(amount) * 100) / 100,
            concept: String(form.get('concept')).trim(),
            detail: String(form.get('detail')).trim(),
            date: `${form.get('date')}T${new Date().toTimeString().slice(0, 5)}`,
            updated_at: new Date().toISOString(),
          }
          if (!Number.isFinite(row.amount) || row.amount <= 0 || !row.concept)
            return
          save.mutate(() => saveRow('transactions', row), { onSuccess: close })
        }}
      >
        <fieldset disabled={save.isPending}>
          {type === 'IN' ? (
            <Field label="Servicio">
              <select
                name="concept"
                required
                defaultValue=""
                onChange={(event) => {
                  const service = services.find(
                    ([name]) => name === event.target.value,
                  )
                  if (service) setAmount(String(service[1]))
                }}
              >
                <option value="" disabled>
                  Selecciona un servicio
                </option>
                {services.map(([name, price]) => (
                  <option key={name} value={name}>
                    {name} · {money(price)}
                  </option>
                ))}
                <option>Otro ingreso</option>
              </select>
            </Field>
          ) : (
            <Field label="Concepto">
              <input
                name="concept"
                required
                maxLength={160}
                placeholder="Ej. Productos de limpieza"
                pattern=".*\S.*"
              />
            </Field>
          )}
          <Field label="Monto (MXN)">
            <MoneyInput
              name="amount"
              min="0.01"
              max="9999999"
              required
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              placeholder="0.00"
            />
          </Field>
          <Field label="Fecha">
            <input name="date" type="date" required defaultValue={isoDate()} />
          </Field>
          <Field label="Detalle">
            <textarea
              name="detail"
              rows={2}
              maxLength={1500}
              placeholder="Mascota, cliente o una nota"
            />
          </Field>
          {save.error && (
            <p className="error" role="alert">
              {errorMessage(save.error)}
            </p>
          )}
          <div className="form-actions">
            <button className="secondary" type="button" onClick={close}>
              Cancelar
            </button>
            <button className="primary" type="submit">
              {save.isPending ? 'Guardando…' : 'Guardar movimiento'}
            </button>
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}
