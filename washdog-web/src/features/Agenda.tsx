import { useState } from 'react'
import {
  Check,
  ChevronLeft,
  ChevronRight,
  MessageCircle,
  Pencil,
  Plus,
  Repeat2,
  Trash2,
} from 'lucide-react'
import { Empty, Field, Modal } from '../components'
import type { Appointment, Snapshot } from '../data/types'
import { deleteRow, errorMessage, saveRow, useSave } from '../data/useData'
import { supabase } from '../data/supabase'
import {
  addDays,
  appointmentClient,
  findConflict,
  isoDate,
  readableDate,
  recurringAppointments,
  services,
  whatsapp,
} from '../domain'

export function AppointmentList({
  appointments,
  data,
  onEdit,
  onDelete,
  onComplete,
  onStop,
}: {
  appointments: Appointment[]
  data: Snapshot
  onEdit: (a: Appointment) => void
  onDelete: (a: Appointment) => void
  onComplete: (a: Appointment) => void
  onStop: (a: Appointment) => void
}) {
  return (
    <div className="appointment-list">
      {appointments.map((a) => {
        const client = appointmentClient(a, data.clients)
        const link =
          client &&
          whatsapp(
            client.phone,
            data.settings.tpl_confirm,
            client.owner_name,
            readableDate(a.date),
            a.time,
          )
        return (
          <article
            className={`appointment-row ${a.status === 'DONE' ? 'completed' : ''}`}
            key={a.id}
          >
            <div className="appointment-time">
              <strong>{a.time}</strong>
              <span>{a.end_time || '1 h'}</span>
            </div>
            <div className="appointment-info">
              <h3>
                {client?.pet_name ?? a.pet_name}
                {a.repeat_every_days && (
                  <Repeat2 size={15} aria-label="Cita recurrente" />
                )}
              </h3>
              <p>{a.service}</p>
              <small>
                {client?.owner_name ?? a.client_name}
                {a.status === 'DONE' && ' · Completada'}
              </small>
              {a.notes && <p className="appointment-notes">{a.notes}</p>}
            </div>
            <div className="row-actions">
              {link && (
                <a
                  className="icon-button"
                  href={link}
                  target="_blank"
                  rel="noreferrer"
                  aria-label={`Confirmar por WhatsApp a ${a.pet_name}`}
                >
                  <MessageCircle size={18} />
                </a>
              )}
              {a.status !== 'DONE' && (
                <button
                  className="icon-button"
                  aria-label={`Completar cita de ${a.pet_name}`}
                  onClick={() => onComplete(a)}
                >
                  <Check size={19} />
                </button>
              )}
              <button
                className="icon-button"
                aria-label={`Editar cita de ${a.pet_name}`}
                onClick={() => onEdit(a)}
              >
                <Pencil size={17} />
              </button>
              {a.repeat_every_days && (
                <button
                  className="icon-button"
                  aria-label={`Detener recurrencia de ${a.pet_name}`}
                  onClick={() => onStop(a)}
                >
                  <Repeat2 size={17} />
                </button>
              )}
              <button
                className="icon-button danger"
                aria-label={`Eliminar cita de ${a.pet_name}`}
                onClick={() => onDelete(a)}
              >
                <Trash2 size={17} />
              </button>
            </div>
          </article>
        )
      })}
    </div>
  )
}

export function Agenda({
  data,
  uid,
  initialNew = false,
}: {
  data: Snapshot
  uid: string
  initialNew?: boolean
}) {
  const [date, setDate] = useState(isoDate())
  const [editing, setEditing] = useState<Appointment | 'new' | null>(
    initialNew ? 'new' : null,
  )
  const [action, setAction] = useState<{
    a: Appointment
    type: 'delete' | 'complete' | 'stop'
  } | null>(null)
  const save = useSave(uid)
  const dayAppointments = data.appointments
    .filter((a) => a.date === date)
    .sort((a, b) => a.time.localeCompare(b.time))
  const weekStart = addDays(
    date,
    -((new Date(`${date}T12:00:00`).getDay() + 6) % 7),
  )
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Agenda</h1>
          <p>Un espacio para cada colita.</p>
        </div>
        <button className="primary" onClick={() => setEditing('new')}>
          <Plus size={18} />
          Nueva cita
        </button>
      </div>
      <div className="date-toolbar">
        <button
          className="icon-button"
          aria-label="Semana anterior"
          onClick={() => setDate(addDays(date, -7))}
        >
          <ChevronLeft />
        </button>
        <input
          aria-label="Fecha de la agenda"
          type="date"
          value={date}
          onChange={(event) => {
            if (event.target.value) setDate(event.target.value)
          }}
        />
        <button
          className="icon-button"
          aria-label="Semana siguiente"
          onClick={() => setDate(addDays(date, 7))}
        >
          <ChevronRight />
        </button>
        <button className="text-button" onClick={() => setDate(isoDate())}>
          Hoy
        </button>
      </div>
      <div className="week-strip">
        {Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)).map(
          (day) => (
            <button
              aria-pressed={day === date}
              key={day}
              onClick={() => setDate(day)}
            >
              <span>
                {new Date(`${day}T12:00:00`).toLocaleDateString('es-MX', {
                  weekday: 'short',
                })}
              </span>
              <strong>{Number(day.slice(-2))}</strong>
              <span className="day-count">
                {data.appointments.filter((a) => a.date === day).length || '·'}
              </span>
            </button>
          ),
        )}
      </div>
      <div className="section-heading">
        <h2>{readableDate(date)}</h2>
        <span>{dayAppointments.length} citas</span>
      </div>
      <AppointmentList
        appointments={dayAppointments}
        data={data}
        onEdit={setEditing}
        onDelete={(a) => {
          save.reset()
          setAction({ a, type: 'delete' })
        }}
        onComplete={(a) => {
          save.reset()
          setAction({ a, type: 'complete' })
        }}
        onStop={(a) => {
          save.reset()
          setAction({ a, type: 'stop' })
        }}
      />
      {!dayAppointments.length && (
        <Empty>
          Un día con espacio para algo bueno.
          <button className="text-button" onClick={() => setEditing('new')}>
            Agendar una cita
          </button>
        </Empty>
      )}
      {editing && (
        <AppointmentForm
          data={data}
          uid={uid}
          date={date}
          appointment={editing === 'new' ? undefined : editing}
          close={() => setEditing(null)}
        />
      )}
      {action && (
        <Modal
          title={
            action.type === 'complete'
              ? '¿Marcar como completada?'
              : action.type === 'stop'
                ? '¿Detener citas recurrentes?'
                : '¿Eliminar esta cita?'
          }
          close={() => setAction(null)}
          busy={save.isPending}
        >
          <p>
            {action.a.pet_name} · {readableDate(action.a.date)} ·{' '}
            {action.a.time}
          </p>
          <p>
            {action.type === 'complete'
              ? 'Se actualizará la última visita del cliente. Registra el cobro en Ingresos.'
              : action.type === 'stop'
                ? 'Se eliminarán las citas pendientes de esta serie a partir de hoy. Las visitas completadas se conservarán.'
                : 'Se eliminará esta cita de todos tus dispositivos.'}
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
              onClick={() => setAction(null)}
            >
              Cancelar
            </button>
            <button
              className="primary"
              disabled={save.isPending}
              onClick={() =>
                save.mutate(
                  async () => {
                    if (action.type === 'delete')
                      await deleteRow('appointments', action.a.id, uid)
                    else if (action.type === 'complete')
                      await saveRow('appointments', {
                        ...action.a,
                        status: 'DONE',
                        updated_at: new Date().toISOString(),
                      })
                    else {
                      const { error } = await supabase!.rpc(
                        'washdog_stop_series',
                        {
                          p_series_id: action.a.series_id!,
                          p_today: isoDate(),
                        },
                      )
                      if (error) throw error
                    }
                  },
                  { onSuccess: () => setAction(null) },
                )
              }
            >
              {save.isPending ? 'Guardando…' : 'Confirmar'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

function AppointmentForm({
  data,
  uid,
  date,
  appointment,
  close,
}: {
  data: Snapshot
  uid: string
  date: string
  appointment?: Appointment
  close: () => void
}) {
  const [validation, setValidation] = useState('')
  const [id] = useState(() => appointment?.id ?? crypto.randomUUID())
  const [seriesId] = useState(
    () => appointment?.series_id ?? crypto.randomUUID(),
  )
  const save = useSave(uid)
  const linked = appointment && appointmentClient(appointment, data.clients)
  return (
    <Modal
      title={appointment ? 'Editar cita' : 'Una cita, una colita feliz'}
      close={close}
      busy={save.isPending}
    >
      {!data.clients.length ? (
        <Empty>Primero añade un cliente desde la sección Clientes.</Empty>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            setValidation('')
            const form = new FormData(event.currentTarget)
            const client = data.clients.find(
              (c) => c.id === form.get('client'),
            )!
            const every = Number(form.get('repeat')) || null
            const base: Appointment = {
              id,
              user_id: uid,
              client_id: client.id,
              client_name: client.owner_name,
              pet_name: client.pet_name,
              service: String(form.get('service')),
              time: String(form.get('time')),
              end_time: String(form.get('end')),
              date: String(form.get('date')),
              notes: String(form.get('notes')).trim(),
              status: appointment?.status ?? 'SCHEDULED',
              repeat_every_days: appointment
                ? appointment.repeat_every_days
                : every,
              series_id: appointment
                ? appointment.series_id
                : every
                  ? seriesId
                  : null,
              updated_at: new Date().toISOString(),
            }
            if (base.end_time <= base.time) {
              setValidation(
                'La hora de salida debe ser posterior a la de llegada.',
              )
              return
            }
            const rows = appointment ? [base] : recurringAppointments(base)
            const conflict = rows
              .map((row) => findConflict(row, data.appointments))
              .find(Boolean)
            if (conflict) {
              setValidation(
                `Ese horario coincide con ${conflict.pet_name} el ${readableDate(conflict.date)} a las ${conflict.time}.`,
              )
              return
            }
            save.mutate(
              async () => {
                const { data: saved, error } = await supabase!
                  .from('appointments')
                  .upsert(rows)
                  .select('id')
                if (error) throw error
                if (saved?.length !== rows.length)
                  throw new Error('WASHDOG_STALE')
              },
              { onSuccess: close },
            )
          }}
        >
          <fieldset disabled={save.isPending}>
            <Field label="Mascota y cliente">
              <select name="client" required defaultValue={linked?.id ?? ''}>
                <option value="" disabled>
                  Selecciona un cliente
                </option>
                {[...data.clients]
                  .sort((a, b) => a.pet_name.localeCompare(b.pet_name, 'es'))
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.pet_name} · {c.owner_name}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Servicio">
              <select
                name="service"
                defaultValue={appointment?.service ?? services[0][0]}
              >
                {appointment &&
                  !services.some(([name]) => name === appointment.service) && (
                    <option>{appointment.service}</option>
                  )}
                {services.map(([name]) => (
                  <option key={name}>{name}</option>
                ))}
              </select>
            </Field>
            <Field label="Fecha">
              <input
                type="date"
                name="date"
                required
                defaultValue={appointment?.date ?? date}
              />
            </Field>
            <div className="form-grid">
              <Field label="Llegada">
                <input
                  type="time"
                  name="time"
                  required
                  defaultValue={appointment?.time ?? '09:00'}
                />
              </Field>
              <Field label="Salida">
                <input
                  type="time"
                  name="end"
                  required
                  defaultValue={appointment?.end_time || '10:00'}
                />
              </Field>
            </div>
            {!appointment && (
              <Field label="Repetir">
                <select name="repeat">
                  <option value="">Una sola vez</option>
                  <option value="7">Cada semana</option>
                  <option value="14">Cada 2 semanas</option>
                  <option value="21">Cada 3 semanas</option>
                  <option value="30">Cada 30 días</option>
                </select>
              </Field>
            )}
            {appointment?.series_id && (
              <p className="muted">
                Este cambio se aplica solamente a esta cita.
              </p>
            )}
            <Field label="Notas">
              <textarea
                name="notes"
                rows={3}
                maxLength={1500}
                defaultValue={appointment?.notes}
              />
            </Field>
            {(validation || save.error) && (
              <p className="error" role="alert">
                {validation || errorMessage(save.error)}
              </p>
            )}
            <div className="form-actions">
              <button type="button" className="secondary" onClick={close}>
                Cancelar
              </button>
              <button className="primary" type="submit">
                {save.isPending ? 'Guardando…' : 'Guardar cita'}
              </button>
            </div>
          </fieldset>
        </form>
      )}
    </Modal>
  )
}
