import { useState } from 'react'
import {
  ClipboardPaste,
  MessageCircle,
  Pencil,
  Phone,
  Plus,
  Search,
  Trash2,
} from 'lucide-react'
import type { Client, Snapshot } from '../data/types'
import { deleteRow, errorMessage, saveRow, useSave } from '../data/useData'
import { Empty, Field, Modal } from '../components'
import { parseClientMessage } from '../clientParser'
import { isoDate, normalized, readableDate, whatsapp } from '../domain'

export function Clients({ data, uid }: { data: Snapshot; uid: string }) {
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Client | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Client | null>(null)
  const save = useSave(uid)
  const clients = data.clients
    .filter((client) =>
      normalized(
        `${client.owner_name} ${client.pet_name} ${client.breed} ${client.phone}`,
      ).includes(normalized(search)),
    )
    .sort((a, b) => a.owner_name.localeCompare(b.owner_name, 'es'))
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Clientes</h1>
          <p>{data.clients.length} compañeros de Washdog</p>
        </div>
        <button className="primary" onClick={() => setEditing('new')}>
          <Plus size={18} />
          Nuevo cliente
        </button>
      </div>
      <label className="search">
        <Search size={19} />
        <input
          aria-label="Buscar clientes"
          placeholder="Busca una persona, mascota o teléfono"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </label>
      <div className="client-list">
        {clients.map((client) => {
          const link = whatsapp(
            client.phone,
            data.settings.tpl_welcome,
            client.owner_name,
          )
          return (
            <article className="client-row" key={client.id}>
              <div className="pet-avatar" aria-hidden="true">
                {client.pet_name.slice(0, 1).toUpperCase()}
              </div>
              <div className="client-info">
                <h2>
                  {client.pet_name}{' '}
                  <span>
                    {client.pet_type === 'CAT' ? 'Gato' : 'Perro'}
                    {client.breed && ` · ${client.breed}`}
                  </span>
                </h2>
                <p>{client.owner_name}</p>
                <small>
                  {client.last_visit
                    ? `Última visita: ${readableDate(client.last_visit)}`
                    : 'Sin visitas registradas'}
                </small>
              </div>
              <div className="row-actions">
                {link && (
                  <a
                    className="icon-button"
                    href={link}
                    target="_blank"
                    rel="noreferrer"
                    aria-label={`WhatsApp a ${client.owner_name}`}
                  >
                    <MessageCircle size={19} />
                  </a>
                )}
                {client.phone && (
                  <a
                    className="icon-button"
                    href={`tel:${client.phone.replace(/[^\d+]/g, '')}`}
                    aria-label={`Llamar a ${client.owner_name}`}
                  >
                    <Phone size={18} />
                  </a>
                )}
                <button
                  className="icon-button"
                  aria-label={`Editar a ${client.pet_name}`}
                  onClick={() => setEditing(client)}
                >
                  <Pencil size={18} />
                </button>
                <button
                  className="icon-button danger"
                  aria-label={`Eliminar a ${client.pet_name}`}
                  onClick={() => {
                    save.reset()
                    setDeleting(client)
                  }}
                >
                  <Trash2 size={18} />
                </button>
              </div>
            </article>
          )
        })}
      </div>
      {!clients.length && (
        <Empty>
          {search
            ? 'No encontramos clientes con esa búsqueda.'
            : 'Aquí empieza una nueva amistad.'}
          <button className="text-button" onClick={() => setEditing('new')}>
            Añadir tu primer cliente
          </button>
        </Empty>
      )}
      {editing && (
        <ClientForm
          client={editing === 'new' ? undefined : editing}
          uid={uid}
          close={() => setEditing(null)}
        />
      )}
      {deleting && (
        <Modal
          title={`¿Eliminar a ${deleting.pet_name}?`}
          close={() => setDeleting(null)}
          busy={save.isPending}
        >
          <p>
            Se eliminarán este cliente y sus citas. Los movimientos de dinero se
            conservarán.
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
                save.mutate(() => deleteRow('clients', deleting.id, uid), {
                  onSuccess: () => setDeleting(null),
                })
              }
            >
              {save.isPending ? 'Eliminando…' : 'Eliminar cliente'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

function ClientForm({
  client,
  uid,
  close,
}: {
  client?: Client
  uid: string
  close: () => void
}) {
  const save = useSave(uid)
  const [id] = useState(() => client?.id ?? crypto.randomUUID())
  const [owner, setOwner] = useState(client?.owner_name ?? '')
  const [pet, setPet] = useState(client?.pet_name ?? '')
  const [breed, setBreed] = useState(client?.breed ?? '')
  const [phone, setPhone] = useState(client?.phone ?? '')
  const [type, setType] = useState<Client['pet_type']>(
    client?.pet_type ?? 'DOG',
  )
  const [message, setMessage] = useState('')
  const [pasteNote, setPasteNote] = useState('')

  const fillFrom = (text: string) => {
    setMessage(text)
    if (!text.trim()) return setPasteNote('')
    const found = parseClientMessage(text)
    const names: string[] = []
    if (found.owner_name) (setOwner(found.owner_name), names.push('persona'))
    if (found.pet_name) (setPet(found.pet_name), names.push('mascota'))
    if (found.breed) (setBreed(found.breed), names.push('raza'))
    if (found.phone) (setPhone(found.phone), names.push('teléfono'))
    if (found.pet_type) setType(found.pet_type)
    setPasteNote(
      names.length
        ? `Se llenó: ${names.join(', ')}. Revisa los datos antes de guardar.`
        : 'No encontré datos en ese mensaje. Prueba con "Nombre:", "Mascota:" y "Teléfono:".',
    )
  }
  const pasteFromClipboard = async () => {
    try {
      fillFrom(await navigator.clipboard.readText())
    } catch {
      setPasteNote(
        'No pude leer el portapapeles. Pega el mensaje en el cuadro de arriba.',
      )
    }
  }
  return (
    <Modal
      title={client ? 'Editar cliente' : 'Un nuevo compañero'}
      close={close}
      busy={save.isPending}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          const form = new FormData(event.currentTarget)
          const row: Client = {
            id,
            user_id: uid,
            owner_name: owner.trim(),
            pet_name: pet.trim(),
            breed: breed.trim(),
            phone: phone.trim(),
            pet_type: type,
            last_visit: String(form.get('visit')),
            needs_reschedule: client?.needs_reschedule ?? false,
            updated_at: new Date().toISOString(),
          }
          if (!row.owner_name || !row.pet_name) return
          save.mutate(() => saveRow('clients', row), { onSuccess: close })
        }}
      >
        <fieldset disabled={save.isPending}>
          {!client && (
            <div className="paste-box">
              <Field label="Mensaje de WhatsApp">
                <textarea
                  aria-label="Mensaje de WhatsApp"
                  rows={4}
                  placeholder="Pega aquí el mensaje con los datos del cliente"
                  value={message}
                  onChange={(event) => fillFrom(event.target.value)}
                />
              </Field>
              <button
                className="secondary"
                type="button"
                onClick={pasteFromClipboard}
              >
                <ClipboardPaste size={17} />
                Pegar del portapapeles
              </button>
              {pasteNote && (
                <p className="field-hint" role="status">
                  {pasteNote}
                </p>
              )}
            </div>
          )}
          <Field label="Nombre de la persona">
            <input
              name="owner"
              value={owner}
              onChange={(event) => setOwner(event.target.value)}
              autoComplete="name"
              required
              maxLength={120}
              pattern=".*\S.*"
            />
          </Field>
          <div className="form-grid">
            <Field label="Nombre de la mascota">
              <input
                name="pet"
                value={pet}
                onChange={(event) => setPet(event.target.value)}
                required
                maxLength={120}
                pattern=".*\S.*"
              />
            </Field>
            <Field label="Tipo">
              <select
                name="type"
                value={type}
                onChange={(event) =>
                  setType(event.target.value as Client['pet_type'])
                }
              >
                <option value="DOG">Perro</option>
                <option value="CAT">Gato</option>
              </select>
            </Field>
          </div>
          <Field label="Raza">
            <input
              name="breed"
              value={breed}
              onChange={(event) => setBreed(event.target.value)}
              maxLength={120}
            />
          </Field>
          <Field label="Teléfono / WhatsApp">
            <input
              name="phone"
              type="tel"
              autoComplete="tel"
              placeholder="10 dígitos o código internacional"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              pattern="[+0-9 ()\-]{10,25}"
            />
          </Field>
          <Field label="Última visita">
            <input
              name="visit"
              type="date"
              max={isoDate()}
              defaultValue={client?.last_visit}
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
              {save.isPending ? 'Guardando…' : 'Guardar cliente'}
            </button>
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}
