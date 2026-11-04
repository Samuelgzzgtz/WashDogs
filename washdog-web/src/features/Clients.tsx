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
import {
  clientFromParsed,
  findDuplicateClient,
  parseClientMessage,
} from '../clientParser'
import { isoDate, normalized, readableDate, whatsapp } from '../domain'

export function Clients({ data, uid }: { data: Snapshot; uid: string }) {
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Client | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Client | null>(null)
  const save = useSave(uid)
  const clients = data.clients
    .filter((client) =>
      normalized(
        `${client.owner_name} ${client.pet_name} ${client.breed} ${client.phone} ${client.address ?? ''}`,
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
                  {client.nervous && (
                    <span className="nervous-tag">Nervioso</span>
                  )}
                </h2>
                <p>{client.owner_name}</p>
                {client.address && (
                  <p className="client-address">{client.address}</p>
                )}
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
          clients={data.clients}
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
  clients,
  uid,
  close,
}: {
  client?: Client
  clients: Client[]
  uid: string
  close: () => void
}) {
  const save = useSave(uid)
  const [id] = useState(() => client?.id ?? crypto.randomUUID())
  const [owner, setOwner] = useState(client?.owner_name ?? '')
  const [pet, setPet] = useState(client?.pet_name ?? '')
  const [breed, setBreed] = useState(client?.breed ?? '')
  const [phone, setPhone] = useState(client?.phone ?? '')
  const [address, setAddress] = useState(client?.address ?? '')
  const [nervous, setNervous] = useState(client?.nervous ?? false)
  const [type, setType] = useState<Client['pet_type']>(
    client?.pet_type ?? 'DOG',
  )
  const [message, setMessage] = useState('')
  const [note, setNote] = useState('')
  // Un cliente nuevo se crea solo pegando el mensaje; los campos aparecen únicamente
  // al editar o si el mensaje no trae lo mínimo (persona y mascota).
  const [manual, setManual] = useState(Boolean(client))

  const addFromMessage = (text: string) => {
    setMessage(text)
    save.reset()
    if (!text.trim()) return setNote('Pega primero el mensaje del cliente.')
    const found = parseClientMessage(text)
    const row = clientFromParsed(found, uid, id)
    if (!row) {
      setOwner(found.owner_name ?? '')
      setPet(found.pet_name ?? '')
      setBreed(found.breed ?? '')
      setPhone(found.phone ?? '')
      setAddress(found.address ?? '')
      setNervous(found.nervous ?? false)
      setType(found.pet_type ?? 'DOG')
      setManual(true)
      setNote(
        found.owner_name || found.pet_name
          ? `No encontré ${found.owner_name ? 'el nombre de la mascota' : 'el nombre de la persona'}. Complétalo y guarda.`
          : 'No encontré el nombre de la persona ni de la mascota. Escríbelos y guarda.',
      )
      return
    }
    const duplicate = findDuplicateClient(clients, row)
    if (duplicate) {
      return setNote(
        `${duplicate.pet_name} de ${duplicate.owner_name} ya está en tus clientes.`,
      )
    }
    setNote('')
    save.mutate(() => saveRow('clients', row), { onSuccess: close })
  }
  const pasteFromClipboard = async () => {
    try {
      addFromMessage(await navigator.clipboard.readText())
    } catch {
      setNote('No pude leer el portapapeles. Pega el mensaje en el cuadro.')
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
          if (!manual) return addFromMessage(message)
          const form = new FormData(event.currentTarget)
          const row: Client = {
            id,
            user_id: uid,
            owner_name: owner.trim(),
            pet_name: pet.trim(),
            breed: breed.trim(),
            phone: phone.trim(),
            address: address.trim(),
            nervous,
            pet_type: type,
            last_visit: String(form.get('visit') ?? ''),
            needs_reschedule: client?.needs_reschedule ?? false,
            updated_at: new Date().toISOString(),
          }
          if (!row.owner_name || !row.pet_name) return
          if (!client) {
            const duplicate = findDuplicateClient(clients, row)
            if (duplicate)
              return setNote(
                `${duplicate.pet_name} de ${duplicate.owner_name} ya está en tus clientes.`,
              )
          }
          save.mutate(() => saveRow('clients', row), { onSuccess: close })
        }}
      >
        <fieldset disabled={save.isPending}>
          {!client && (
            <div className="paste-box">
              <Field label="Mensaje de WhatsApp">
                <textarea
                  aria-label="Mensaje de WhatsApp"
                  rows={manual ? 4 : 7}
                  autoFocus
                  placeholder="Pega aquí el mensaje con los datos del cliente y se guarda solo"
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                  onPaste={(event) => {
                    event.preventDefault()
                    addFromMessage(event.clipboardData.getData('text'))
                  }}
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
              {note && (
                <p className="field-hint" role="status">
                  {note}
                </p>
              )}
            </div>
          )}
          {manual && (
            <>
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
              <Field label="Dirección">
                <input
                  name="address"
                  autoComplete="street-address"
                  value={address}
                  onChange={(event) => setAddress(event.target.value)}
                  maxLength={300}
                />
              </Field>
              <label className="check-row">
                <input
                  name="nervous"
                  type="checkbox"
                  checked={nervous}
                  onChange={(event) => setNervous(event.target.checked)}
                />
                <span>Es nervioso</span>
              </label>
              <Field label="Última visita">
                <input
                  name="visit"
                  type="date"
                  max={isoDate()}
                  defaultValue={client?.last_visit}
                />
              </Field>
            </>
          )}
          {save.error && (
            <p className="error" role="alert">
              {errorMessage(save.error)}
            </p>
          )}
          <div className="form-actions">
            <button className="secondary" type="button" onClick={close}>
              Cancelar
            </button>
            <button
              className="primary"
              type="submit"
              disabled={!manual && !message.trim()}
            >
              {save.isPending ? 'Guardando…' : 'Guardar cliente'}
            </button>
          </div>
        </fieldset>
      </form>
    </Modal>
  )
}
