import type {
  Appointment,
  AppointmentSeries,
  Client,
  Tombstone,
  Transaction,
} from './data/types'

export const services = [
  ['Baño Perro Pequeño', 200],
  ['Baño Perro Mediano', 250],
  ['Baño Perro Grande', 300],
  ['Baño Perro Jumbo', 375],
  ['Baño Gato Pelo Corto', 250],
  ['Baño Gato Pelo Largo', 300],
  ['Corte de Uñas', 50],
  ['Detalles Mínimos c/Tijera', 50],
  ['Corte Higiénico', 50],
  ['Limpieza de Oídos', 35],
  ['Deslanado', 120],
  ['Estética Canina Básica', 150],
  ['Rapa Completa', 120],
  ['Acondicionador', 50],
  ['Domicilio · Sector Granada', 30],
  ['Domicilio · Mitras Poniente', 50],
  ['Domicilio · Alrededores', 60],
] as const

export function isoDate(date = new Date()): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
export function addDays(date: string, days: number): string {
  const value = new Date(`${date}T12:00:00`)
  value.setDate(value.getDate() + days)
  return isoDate(value)
}
export const money = (amount: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(
    amount,
  )
export const readableDate = (date: string) =>
  new Intl.DateTimeFormat('es-MX', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(`${date}T12:00:00`))
export const normalized = (value: string) =>
  value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()

export function whatsapp(
  phone: string,
  template: string,
  name: string,
  date = '',
  time = '',
): string | null {
  let digits = phone.replace(/\D/g, '')
  if (digits.length === 10) digits = `52${digits}`
  if (digits.length < 11 || digits.length > 15) return null
  const message = template
    .replaceAll('{{nombre}}', name)
    .replaceAll('{{fecha}}', date)
    .replaceAll('{{hora}}', time)
  return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`
}

export function appointmentClient(appointment: Appointment, clients: Client[]) {
  return clients.find((client) =>
    appointment.client_id
      ? client.id === appointment.client_id
      : client.owner_name === appointment.client_name &&
        client.pet_name === appointment.pet_name,
  )
}

export function findConflict(
  candidate: Appointment,
  existing: Appointment[],
): Appointment | undefined {
  const end = (a: Appointment) =>
    a.end_time ||
    `${String(Number(a.time.slice(0, 2)) + 1).padStart(2, '0')}${a.time.slice(2)}`
  return existing.find(
    (a) =>
      a.id !== candidate.id &&
      a.date === candidate.date &&
      candidate.time < end(a) &&
      end(candidate) > a.time,
  )
}

export function recurringAppointments(
  base: Appointment,
  through = addDays(base.date, 90),
): Appointment[] {
  const interval = base.repeat_every_days
  if (!interval || interval <= 0 || !base.series_id) return [base]
  const result = [base]
  for (
    let date = addDays(base.date, interval);
    date <= through;
    date = addDays(date, interval)
  ) {
    result.push({
      ...base,
      id: `${base.series_id}@${date}`,
      date,
      status: 'SCHEDULED',
    })
  }
  return result
}

export function missingAppointments(
  appointments: Appointment[],
  tombstones: Tombstone[],
  series: AppointmentSeries[],
  today = isoDate(),
): Appointment[] {
  const existingIds = new Set(appointments.map((a) => a.id))
  const deletedIds = new Set(
    tombstones
      .filter((t) => t.entity === 'appointments')
      .map((t) => t.entity_id),
  )
  const result: Appointment[] = []
  for (const { template: base, stopped_at } of series) {
    if (stopped_at) continue
    for (const row of recurringAppointments(base, addDays(today, 90))) {
      if (
        row.date < today ||
        existingIds.has(row.id) ||
        deletedIds.has(row.id) ||
        findConflict(row, [...appointments, ...result])
      )
        continue
      result.push({ ...row, updated_at: new Date().toISOString() })
    }
  }
  return result
}

export function visibleRows<T extends { id: string; updated_at: string }>(
  rows: T[],
  tombstones: Tombstone[],
  entity: string,
): T[] {
  const deleted = new Map(
    tombstones
      .filter((t) => t.entity === entity)
      .map((t) => [t.entity_id, t.deleted_at]),
  )
  return rows.filter(
    (row) =>
      !deleted.has(row.id) ||
      Date.parse(row.updated_at) > Date.parse(deleted.get(row.id)!),
  )
}

export function clientsToRemind(
  clients: Client[],
  appointments: Appointment[],
  today = isoDate(),
): Client[] {
  return clients.filter(
    (client) =>
      client.last_visit &&
      client.last_visit <= addDays(today, -30) &&
      !appointments.some(
        (a) =>
          a.date >= today &&
          a.status !== 'DONE' &&
          (a.client_id
            ? a.client_id === client.id
            : a.client_name === client.owner_name &&
              a.pet_name === client.pet_name),
      ),
  )
}

export function totals(transactions: Transaction[], month: string) {
  return transactions
    .filter((tx) => tx.date.startsWith(month))
    .reduce(
      (total, tx) => ({
        income: total.income + (tx.type === 'IN' ? tx.amount : 0),
        expense: total.expense + (tx.type === 'OUT' ? tx.amount : 0),
      }),
      { income: 0, expense: 0 },
    )
}

export function transactionsCsv(transactions: Transaction[]): string {
  const cell = (value: string | number) => {
    const text = String(value)
    // Spreadsheet programs interpret leading formula characters even inside quotes.
    return `"${(/^[=+\-@\t\r]/.test(text) ? `'${text}` : text).replaceAll('"', '""')}"`
  }
  return (
    '\ufeff' +
    [
      ['Fecha', 'Tipo', 'Concepto', 'Detalle', 'Monto'],
      ...transactions.map((tx) => [
        tx.date,
        tx.type === 'IN' ? 'Ingreso' : 'Gasto',
        tx.concept,
        tx.detail,
        tx.amount,
      ]),
    ]
      .map((row) => row.map(cell).join(','))
      .join('\r\n')
  )
}
