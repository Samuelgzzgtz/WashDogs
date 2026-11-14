import { useEffect, useMemo, useState } from 'react'
import { Bell, BellOff, Check, MessageCircle, X } from 'lucide-react'
import { Empty, Field } from '../components'
import type { BotAlert, Snapshot } from '../data/types'
import { errorMessage } from '../data/useData'
import { money, readableDate, whatsapp } from '../domain'
import { slotLabel } from '../schedule'
import {
  petKindLabel,
  petKinds,
  sizeLabel,
  sizes,
  type BotConfig,
  type PetKind,
  type PriceTime,
  type Zone,
} from '../bot/config'
import {
  needsReview,
  quote,
  suggestOffers,
  zoneFor,
  type Pet,
} from '../bot/planner'
import {
  disablePush,
  enablePush,
  pushState,
  sendTestPush,
  type PushState,
} from '../bot/push'
import {
  saveBotConfig,
  setAlertStatus,
  useBot,
  useBotSave,
} from '../bot/useBot'

const kindLabel: Record<BotAlert['kind'], string> = {
  BOOKING: 'Quiere agendar',
  SAME_DAY: 'Pide para hoy',
  OUT_OF_ZONE: 'Fuera de zona',
  REVIEW: 'Revisar (foto / nudos)',
  TEST: 'Prueba',
}

export function Bot({ data, uid }: { data: Snapshot; uid: string }) {
  const bot = useBot(uid)
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Tu secretario</h1>
          <p>
            Avisos del bot de WhatsApp, y los precios, tiempos y zonas con los
            que contesta.
          </p>
        </div>
      </div>
      <Notifications uid={uid} />
      {bot.isPending ? (
        <p className="muted" role="status">
          Cargando el bot…
        </p>
      ) : bot.isError || !bot.data ? (
        <p className="error" role="alert">
          No se pudo cargar el bot. Revisa que la base de datos tenga la
          migración del bot aplicada.
        </p>
      ) : (
        <>
          <Alerts alerts={bot.data.alerts} uid={uid} />
          <Simulator data={data} config={bot.data.config} />
          <ConfigEditor
            key={bot.data.savedAt}
            uid={uid}
            config={bot.data.config}
          />
        </>
      )}
    </>
  )
}

/* ---------- Notificaciones ---------- */

function Notifications({ uid }: { uid: string }) {
  const [state, setState] = useState<PushState | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(
    null,
  )
  useEffect(() => {
    void pushState().then(setState)
  }, [])
  const run = async (action: () => Promise<string>) => {
    setBusy(true)
    setMessage(null)
    try {
      setMessage({ ok: true, text: await action() })
    } catch (error) {
      setMessage({
        ok: false,
        text: error instanceof Error ? error.message : errorMessage(error),
      })
    } finally {
      setState(await pushState())
      setBusy(false)
    }
  }
  return (
    <section className="settings-section bot-section">
      <h2>Notificaciones</h2>
      <p>
        Cuando alguien quiera agendar, pida cita para hoy o esté fuera de zona,
        te llega una notificación de Washdog en este dispositivo.
      </p>
      {state === 'needs-install' && (
        <p className="notice">
          En iPhone primero instala la app: en Safari toca Compartir → “Agregar
          a inicio”, ábrela desde el ícono y vuelve aquí.
        </p>
      )}
      {state === 'unsupported' && (
        <p className="notice">
          Este navegador no permite notificaciones. Abre Washdog en Chrome
          (Android) o instalada en la pantalla de inicio (iPhone).
        </p>
      )}
      {state === 'denied' && (
        <p className="notice">
          Las notificaciones están bloqueadas para Washdog. Actívalas en los
          ajustes del teléfono (Notificaciones → Washdog) y recarga la app.
        </p>
      )}
      <div className="bot-actions">
        {state === 'off' && (
          <button
            className="primary"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await enablePush(uid)
                const test = await sendTestPush()
                return test.sent
                  ? 'Listo. Te mandamos una notificación de prueba.'
                  : 'Activadas en este dispositivo.'
              })
            }
          >
            <Bell size={18} />
            {busy ? 'Activando…' : 'Activar notificaciones'}
          </button>
        )}
        {state === 'on' && (
          <>
            <span className="success bot-pill">
              <Bell size={16} /> Activadas en este dispositivo
            </span>
            <button
              className="secondary"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  const test = await sendTestPush()
                  return `Prueba enviada a ${test.sent} de ${test.devices} dispositivo(s).`
                })
              }
            >
              Mandar prueba
            </button>
            <button
              className="text-button"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await disablePush()
                  return 'Este dispositivo ya no recibirá avisos.'
                })
              }
            >
              <BellOff size={17} />
              Desactivar aquí
            </button>
          </>
        )}
      </div>
      {message && (
        <p className={message.ok ? 'success' : 'error'} role="status">
          {message.text}
        </p>
      )}
    </section>
  )
}

/* ---------- Avisos ---------- */

function Alerts({ alerts, uid }: { alerts: BotAlert[]; uid: string }) {
  const save = useBotSave(uid)
  const [showDone, setShowDone] = useState(false)
  const pending = alerts.filter((a) => a.status === 'PENDING')
  const done = alerts.filter((a) => a.status !== 'PENDING').slice(0, 20)
  return (
    <section className="settings-section bot-section">
      <h2>
        Avisos <span className="count">{pending.length}</span>
      </h2>
      <p>
        El bot nunca confirma citas: contesta “déjame checo” y te deja el aviso
        aquí para que tú decidas.
      </p>
      {!pending.length ? (
        <Empty>No hay avisos pendientes.</Empty>
      ) : (
        pending.map((alert) => (
          <AlertCard
            key={alert.id}
            alert={alert}
            busy={save.isPending}
            resolve={(status) =>
              save.mutate(() => setAlertStatus(alert, status))
            }
          />
        ))
      )}
      {save.error && (
        <p className="error" role="alert">
          {errorMessage(save.error)}
        </p>
      )}
      {done.length > 0 && (
        <button
          className="text-button"
          onClick={() => setShowDone((v) => !v)}
        >
          {showDone ? 'Ocultar resueltos' : `Ver resueltos (${done.length})`}
        </button>
      )}
      {showDone &&
        done.map((alert) => (
          <AlertCard key={alert.id} alert={alert} busy={false} />
        ))}
    </section>
  )
}

function AlertCard({
  alert,
  busy,
  resolve,
}: {
  alert: BotAlert
  busy: boolean
  resolve?: (status: BotAlert['status']) => void
}) {
  const link = whatsapp(alert.phone, '', alert.customer_name)
  const when = [
    alert.requested_date && readableDate(alert.requested_date),
    alert.requested_time && slotLabel(alert.requested_time),
  ]
    .filter(Boolean)
    .join(' · ')
  return (
    <article className={`bot-alert kind-${alert.kind.toLowerCase()}`}>
      <div className="bot-alert-head">
        <strong>{kindLabel[alert.kind]}</strong>
        <small>
          {new Date(alert.created_at).toLocaleString('es-MX', {
            weekday: 'short',
            hour: 'numeric',
            minute: '2-digit',
          })}
        </small>
      </div>
      <h3>{alert.customer_name || alert.phone || 'Cliente nuevo'}</h3>
      {when && <p className="bot-alert-when">{when}</p>}
      <p>{alert.summary}</p>
      {alert.status !== 'PENDING' && (
        <small className="muted">
          {alert.status === 'ACCEPTED' ? 'Aceptado' : 'Descartado'}
        </small>
      )}
      <div className="bot-actions">
        {link && (
          <a
            className="secondary"
            href={link}
            target="_blank"
            rel="noreferrer"
          >
            <MessageCircle size={17} />
            Abrir chat
          </a>
        )}
        {resolve && (
          <>
            <button
              className="primary"
              disabled={busy}
              onClick={() => resolve('ACCEPTED')}
            >
              <Check size={17} />
              Aceptar
            </button>
            <button
              className="text-button"
              disabled={busy}
              onClick={() => resolve('REJECTED')}
            >
              <X size={17} />
              Descartar
            </button>
          </>
        )}
      </div>
    </article>
  )
}

/* ---------- Simulador: qué contestaría el bot ---------- */

function Simulator({ data, config }: { data: Snapshot; config: BotConfig }) {
  const [kind, setKind] = useState<PetKind>('PEQUENO')
  const [extras, setExtras] = useState<string[]>([])
  const [matted, setMatted] = useState(false)
  const [address, setAddress] = useState('Sector Granada')
  const zone = zoneFor(address, config)
  const pets: Pet[] = [{ kind, extras, matted }]
  const q = quote(pets, zone, config)
  const offers = useMemo(
    () =>
      zone.mode === 'NORMAL'
        ? suggestOffers(q.minutes, zone, data.appointments, data.clients, config)
        : [],
    [q.minutes, zone, data.appointments, data.clients, config],
  )
  const extraOptions = [
    ...config.sizedExtras.map((e) => ({ id: e.id, name: e.name })),
    ...config.extras.map((e) => ({ id: e.id, name: e.name })),
  ]
  return (
    <section className="settings-section bot-section">
      <h2>Prueba al bot</h2>
      <p>
        Elige un perro y una dirección para ver el precio, cuánto tardaría y
        qué horarios ofrecería según tu agenda.
      </p>
      <div className="form-grid">
        <Field label="Mascota">
          <select
            value={kind}
            onChange={(e) => setKind(e.target.value as PetKind)}
          >
            {petKinds.map((k) => (
              <option key={k} value={k}>
                {petKindLabel[k]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Dirección o colonia">
          <input
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            maxLength={200}
          />
        </Field>
      </div>
      <div className="bot-chips" role="group" aria-label="Extras">
        {extraOptions.map((e) => (
          <label key={e.id} className="bot-chip">
            <input
              type="checkbox"
              checked={extras.includes(e.id)}
              onChange={(ev) =>
                setExtras((list) =>
                  ev.target.checked
                    ? [...list, e.id]
                    : list.filter((id) => id !== e.id),
                )
              }
            />
            <span>{e.name.replace(/\s*\(.*\)$/, '')}</span>
          </label>
        ))}
        <label className="bot-chip">
          <input
            type="checkbox"
            checked={matted}
            onChange={(e) => setMatted(e.target.checked)}
          />
          <span>Muchos nudos</span>
        </label>
      </div>
      <div className="bot-reply">
        <p>
          <strong>Zona:</strong> {zone.name}
        </p>
        {zone.mode === 'ALERT' ? (
          <p>
            🤖 “{config.texts.outOfZone}” → te llega un aviso.
          </p>
        ) : needsReview(pets, config) ? (
          <p>
            🤖 “{config.texts.needsReview}” → te llega un aviso para revisar.
          </p>
        ) : (
          <>
            <ul className="bot-lines">
              {q.lines.map((l) => (
                <li key={l.label}>
                  <span>{l.label}</span>
                  <span>
                    {l.price === 0 ? 'Gratis' : `${l.from ? 'desde ' : ''}${money(l.price)}`}
                  </span>
                </li>
              ))}
              <li className="bot-total">
                <span>Total · {formatMinutes(q.minutes)}</span>
                <span>
                  {q.from ? 'desde ' : ''}
                  {money(q.price)}
                </span>
              </li>
            </ul>
            <p>
              <strong>Horarios que ofrecería:</strong>
            </p>
            {offers.length ? (
              <ul className="bot-offers">
                {offers.map((o) => (
                  <li key={o.date + o.time}>
                    {readableDate(o.date)} · {slotLabel(o.time)} a{' '}
                    {slotLabel(o.end)}
                    {o.sameZone && <em> · ya hay cita en la zona</em>}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="muted">
                No hay horarios en los próximos 7 días; el bot te avisaría.
              </p>
            )}
          </>
        )}
      </div>
    </section>
  )
}

const formatMinutes = (minutes: number) => {
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return [h && `${h} h`, m && `${m} min`].filter(Boolean).join(' ') || '0 min'
}

/* ---------- Editor de configuración ---------- */

function ConfigEditor({ uid, config }: { uid: string; config: BotConfig }) {
  const [draft, setDraft] = useState<BotConfig>(config)
  const [saved, setSaved] = useState(false)
  const save = useBotSave(uid)
  const update = (change: (c: BotConfig) => BotConfig) => {
    setSaved(false)
    setDraft((c) => change(structuredClone(c)))
  }
  const num = (value: string) => Math.max(0, Number(value) || 0)
  const priceTime = (
    item: PriceTime,
    onChange: (item: PriceTime) => void,
    label: string,
  ) => (
    <>
      <input
        aria-label={`Precio ${label}`}
        type="number"
        min={0}
        step={5}
        value={item.price}
        onChange={(e) => onChange({ ...item, price: num(e.target.value) })}
      />
      <input
        aria-label={`Minutos ${label}`}
        type="number"
        min={0}
        step={15}
        value={item.minutes}
        onChange={(e) => onChange({ ...item, minutes: num(e.target.value) })}
      />
      <label className="bot-from">
        <input
          type="checkbox"
          checked={Boolean(item.from)}
          onChange={(e) => onChange({ ...item, from: e.target.checked })}
        />
        <span>desde</span>
      </label>
    </>
  )
  return (
    <form
      className="bot-editor"
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate(() => saveBotConfig(uid, draft), {
          onSuccess: () => setSaved(true),
        })
      }}
    >
      <section className="settings-section bot-section">
        <h2>Encendido</h2>
        <label className="check-row">
          <input
            type="checkbox"
            checked={draft.enabled}
            onChange={(e) =>
              update((c) => ({ ...c, enabled: e.target.checked }))
            }
          />
          <span>
            El bot contesta solo. Si lo apagas, no responde y solo te deja
            avisos.
          </span>
        </label>
      </section>

      <section className="settings-section bot-section">
        <h2>Baños: precio y tiempo</h2>
        <p>Precio en pesos y minutos que tarda cada baño básico.</p>
        <div className="bot-table">
          <div className="bot-table-head">
            <span>Servicio</span>
            <span>Precio</span>
            <span>Min</span>
            <span />
          </div>
          {petKinds.map((k) => (
            <div className="bot-table-row" key={k}>
              <span>{petKindLabel[k]}</span>
              {priceTime(
                draft.baths[k],
                (item) =>
                  update((c) => ({ ...c, baths: { ...c.baths, [k]: item } })),
                petKindLabel[k],
              )}
            </div>
          ))}
        </div>
      </section>

      <section className="settings-section bot-section">
        <h2>Extras por talla</h2>
        <p>Cuánto cuesta y cuánto tiempo suma según la talla del perro.</p>
        {draft.sizedExtras.map((extra, i) => (
          <div className="bot-table" key={extra.id}>
            <input
              className="bot-name"
              aria-label="Nombre del servicio"
              value={extra.name}
              onChange={(e) =>
                update((c) => {
                  c.sizedExtras[i].name = e.target.value
                  return c
                })
              }
            />
            {sizes.map((s) => (
              <div className="bot-table-row" key={s}>
                <span>{sizeLabel[s]}</span>
                {priceTime(
                  extra.bySize[s],
                  (item) =>
                    update((c) => {
                      c.sizedExtras[i].bySize[s] = item
                      return c
                    }),
                  `${extra.name} ${sizeLabel[s]}`,
                )}
              </div>
            ))}
          </div>
        ))}
      </section>

      <section className="settings-section bot-section">
        <h2>Servicios individuales</h2>
        <div className="bot-table">
          <div className="bot-table-head">
            <span>Servicio</span>
            <span>Precio</span>
            <span>Min</span>
            <span />
          </div>
          {draft.extras.map((extra, i) => (
            <div className="bot-table-row" key={extra.id}>
              <input
                aria-label="Nombre del servicio"
                value={extra.name}
                onChange={(e) =>
                  update((c) => {
                    c.extras[i].name = e.target.value
                    return c
                  })
                }
              />
              {priceTime(
                extra,
                (item) =>
                  update((c) => {
                    c.extras[i] = { ...c.extras[i], ...item }
                    return c
                  }),
                extra.name,
              )}
            </div>
          ))}
        </div>
      </section>

      <section className="settings-section bot-section">
        <h2>Zonas y domicilio</h2>
        <p>
          El bot ubica al cliente por palabras de su dirección, en este orden.
          “Solo avisar” = no ofrece horarios y te manda el aviso.
        </p>
        {draft.zones.map((zone, i) => (
          <ZoneEditor
            key={zone.id}
            zone={zone}
            onChange={(z) =>
              update((c) => {
                c.zones[i] = z
                return c
              })
            }
          />
        ))}
      </section>

      <section className="settings-section bot-section">
        <h2>Reglas de agenda</h2>
        <div className="form-grid">
          <Field label="Colchón entre citas (min)">
            <input
              type="number"
              min={0}
              step={5}
              value={draft.rules.marginMinutes}
              onChange={(e) =>
                update((c) => {
                  c.rules.marginMinutes = num(e.target.value)
                  return c
                })
              }
            />
          </Field>
          <Field label="Traslado en la misma zona (min)">
            <input
              type="number"
              min={0}
              step={5}
              value={draft.rules.sameZoneTravelMinutes}
              onChange={(e) =>
                update((c) => {
                  c.rules.sameZoneTravelMinutes = num(e.target.value)
                  return c
                })
              }
            />
          </Field>
          <Field label="Terminar a más tardar">
            <input
              type="time"
              value={draft.rules.latestFinish}
              onChange={(e) =>
                update((c) => {
                  c.rules.latestFinish = e.target.value || '21:30'
                  return c
                })
              }
            />
          </Field>
          <Field label="Perros grandes para revisar">
            <input
              type="number"
              min={1}
              value={draft.rules.reviewLargeDogs}
              onChange={(e) =>
                update((c) => {
                  c.rules.reviewLargeDogs = Math.max(1, num(e.target.value))
                  return c
                })
              }
            />
          </Field>
        </div>
      </section>

      <section className="settings-section bot-section">
        <h2>Cómo contesta</h2>
        {(
          [
            ['greeting', 'Saludo'],
            ['bathIncludes', 'Qué incluye el baño'],
            ['sameDay', 'Si piden para hoy'],
            ['bookingRequested', 'Cuando piden un horario'],
            ['outOfZone', 'Fuera de zona'],
            ['needsReview', 'Nudos o varios perros grandes'],
            ['unknown', 'Si no sabe qué contestar'],
          ] as const
        ).map(([key, label]) => (
          <Field label={label} key={key}>
            <textarea
              rows={3}
              maxLength={1000}
              value={draft.texts[key]}
              onChange={(e) =>
                update((c) => {
                  c.texts[key] = e.target.value
                  return c
                })
              }
            />
          </Field>
        ))}
      </section>

      <div className="bot-save">
        {save.error && (
          <p className="error" role="alert">
            {errorMessage(save.error)}
          </p>
        )}
        {saved && (
          <p className="success" role="status">
            Cambios guardados. El bot ya contesta con esto.
          </p>
        )}
        <button className="primary" type="submit" disabled={save.isPending}>
          {save.isPending ? 'Guardando…' : 'Guardar cambios del bot'}
        </button>
      </div>
    </form>
  )
}

function ZoneEditor({
  zone,
  onChange,
}: {
  zone: Zone
  onChange: (zone: Zone) => void
}) {
  return (
    <div className="bot-zone">
      <input
        className="bot-name"
        aria-label="Nombre de la zona"
        value={zone.name}
        onChange={(e) => onChange({ ...zone, name: e.target.value })}
      />
      <Field label="Palabras en la dirección (separadas por coma)">
        <input
          value={zone.keywords.join(', ')}
          onChange={(e) =>
            onChange({
              ...zone,
              keywords: e.target.value.split(',').map((k) => k.trimStart()),
            })
          }
        />
      </Field>
      <div className="bot-zone-grid">
        <Field label="Domicilio $">
          <input
            type="number"
            min={0}
            step={10}
            value={zone.fee}
            onChange={(e) =>
              onChange({ ...zone, fee: Math.max(0, Number(e.target.value) || 0) })
            }
          />
        </Field>
        <Field label="Traslado (min)">
          <input
            type="number"
            min={0}
            step={5}
            value={zone.travelMinutes}
            onChange={(e) =>
              onChange({
                ...zone,
                travelMinutes: Math.max(0, Number(e.target.value) || 0),
              })
            }
          />
        </Field>
        <Field label="El bot…">
          <select
            value={zone.mode}
            onChange={(e) =>
              onChange({ ...zone, mode: e.target.value as Zone['mode'] })
            }
          >
            <option value="NORMAL">Ofrece horarios</option>
            <option value="ALERT">Solo avisar</option>
          </select>
        </Field>
      </div>
      <label className="check-row">
        <input
          type="checkbox"
          checked={Boolean(zone.feeFrom)}
          onChange={(e) => onChange({ ...zone, feeFrom: e.target.checked })}
        />
        <span>El domicilio es “desde” (puede subir)</span>
      </label>
    </div>
  )
}
