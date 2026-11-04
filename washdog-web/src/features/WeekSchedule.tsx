import { ChevronLeft, ChevronRight } from 'lucide-react'
import type { Appointment, Client } from '../data/types'
import { addDays, appointmentClient, isoDate } from '../domain'
import { mondayOf, slotLabel, weekSchedule, type Slot } from '../schedule'

export type PickedSlot = { date: string; slot: Slot }

const dayName = (date: string, style: 'long' | 'short') =>
  new Date(`${date}T12:00:00`).toLocaleDateString('es-MX', { weekday: style })
const dayNumber = (date: string) => Number(date.slice(-2))
const shortDate = (date: string) =>
  new Date(`${date}T12:00:00`).toLocaleDateString('es-MX', {
    day: 'numeric',
    month: 'short',
  })

/**
 * Calendario semanal con los horarios de siempre. Los libres se pueden elegir
 * (`onPick`) y los ocupados muestran la mascota y el cliente que los tiene (`onOpen`).
 * Sin `onPick` o `onOpen`, ese tipo de horario queda solo como vista.
 */
export function WeekSchedule({
  appointments,
  clients,
  date,
  onDateChange,
  selected,
  onPick,
  onOpen,
  wide = false,
}: {
  appointments: Appointment[]
  clients: Client[]
  /** Cualquier fecha de la semana que se muestra. */
  date: string
  onDateChange: (date: string) => void
  selected?: PickedSlot | null
  onPick?: (picked: PickedSlot) => void
  onOpen?: (appointment: Appointment) => void
  /** En pantallas anchas muestra los siete días en columnas. */
  wide?: boolean
}) {
  const weekStart = mondayOf(date)
  const today = isoDate()
  const days = weekSchedule(weekStart, appointments)
  const busyButton = (a: Appointment, label: string, extra = false) => {
    const client = appointmentClient(a, clients)
    return (
      <button
        type="button"
        key={a.id}
        className={`slot busy${extra ? ' extra' : ''}`}
        disabled={!onOpen}
        onClick={() => onOpen?.(a)}
        aria-label={`${label}, ocupado por ${client?.pet_name ?? a.pet_name}`}
      >
        <span className="slot-time">{label}</span>
        <strong>{client?.pet_name ?? a.pet_name}</strong>
        <small>{client?.owner_name ?? a.client_name}</small>
      </button>
    )
  }
  return (
    <div className="week-schedule">
      <div className="date-toolbar">
        <button
          type="button"
          className="icon-button"
          aria-label="Semana anterior"
          onClick={() => onDateChange(addDays(weekStart, -7))}
        >
          <ChevronLeft />
        </button>
        <strong className="week-range">
          {shortDate(weekStart)} – {shortDate(addDays(weekStart, 6))}
        </strong>
        <button
          type="button"
          className="icon-button"
          aria-label="Semana siguiente"
          onClick={() => onDateChange(addDays(weekStart, 7))}
        >
          <ChevronRight />
        </button>
        <button
          type="button"
          className="text-button"
          onClick={() => onDateChange(today)}
        >
          Hoy
        </button>
      </div>
      <div className={`week-grid${wide ? ' wide' : ''}`}>
        {days.map((day) => (
          <section
            className={`week-day${day.date === today ? ' today' : ''}`}
            key={day.date}
            aria-label={`${dayName(day.date, 'long')} ${dayNumber(day.date)}`}
          >
            <h3>
              <span>{dayName(day.date, 'short')}</span>
              <strong>{dayNumber(day.date)}</strong>
            </h3>
            {!day.cells.length && !day.extras.length && (
              <p className="slot-none">Sin horario</p>
            )}
            {(day.cells.length > 0 || day.extras.length > 0) && (
            <div className="slots">
            {day.cells.map((cell) => {
              const label = slotLabel(cell.slot.time)
              if (cell.appointment) return busyButton(cell.appointment, label)
              const isSelected =
                selected?.date === cell.date &&
                selected.slot.time === cell.slot.time
              return (
                <button
                  type="button"
                  key={cell.slot.time}
                  className={`slot${isSelected ? ' selected' : ''}${cell.past ? ' past' : ''}`}
                  disabled={!onPick || cell.past}
                  aria-pressed={onPick ? isSelected : undefined}
                  aria-label={`${dayName(cell.date, 'long')} ${dayNumber(cell.date)} a las ${label}, disponible`}
                  onClick={() => onPick?.({ date: cell.date, slot: cell.slot })}
                >
                  <span className="slot-time">{label}</span>
                </button>
              )
            })}
            {day.extras.map((a) => busyButton(a, slotLabel(a.time), true))}
            </div>
            )}
          </section>
        ))}
      </div>
      <p className="slot-legend">
        <span className="legend-dot free" /> Disponible
        <span className="legend-dot busy" /> Ocupado
      </p>
    </div>
  )
}
