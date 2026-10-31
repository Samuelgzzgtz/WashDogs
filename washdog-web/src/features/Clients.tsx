import { useState } from "react";
import {
  MessageCircle,
  Pencil,
  Phone,
  Plus,
  Search,
  Trash2,
} from "lucide-react";
import type { Client, Snapshot } from "../data/types";
import { deleteRow, errorMessage, saveRow, useSave } from "../data/useData";
import { Empty, Field, Modal } from "../components";
import { isoDate, normalized, readableDate, whatsapp } from "../domain";

export function Clients({ data, uid }: { data: Snapshot; uid: string }) {
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Client | "new" | null>(null);
  const [deleting, setDeleting] = useState<Client | null>(null);
  const save = useSave(uid);
  const clients = data.clients
    .filter((client) =>
      normalized(
        `${client.owner_name} ${client.pet_name} ${client.breed} ${client.phone}`,
      ).includes(normalized(search)),
    )
    .sort((a, b) => a.owner_name.localeCompare(b.owner_name, "es"));
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>Clientes</h1>
          <p>{data.clients.length} compañeros de Washdog</p>
        </div>
        <button className="primary" onClick={() => setEditing("new")}>
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
          );
          return (
            <article className="client-row" key={client.id}>
              <div className="pet-avatar" aria-hidden="true">
                {client.pet_name.slice(0, 1).toUpperCase()}
              </div>
              <div className="client-info">
                <h2>
                  {client.pet_name}{" "}
                  <span>
                    {client.pet_type === "CAT" ? "Gato" : "Perro"}
                    {client.breed && ` · ${client.breed}`}
                  </span>
                </h2>
                <p>{client.owner_name}</p>
                <small>
                  {client.last_visit
                    ? `Última visita: ${readableDate(client.last_visit)}`
                    : "Sin visitas registradas"}
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
                    href={`tel:${client.phone.replace(/[^\d+]/g, "")}`}
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
                    save.reset();
                    setDeleting(client);
                  }}
                >
                  <Trash2 size={18} />
                </button>
              </div>
            </article>
          );
        })}
      </div>
      {!clients.length && (
        <Empty>
          {search
            ? "No encontramos clientes con esa búsqueda."
            : "Aquí empieza una nueva amistad."}
          <button className="text-button" onClick={() => setEditing("new")}>
            Añadir tu primer cliente
          </button>
        </Empty>
      )}
      {editing && (
        <ClientForm
          client={editing === "new" ? undefined : editing}
          uid={uid}
          welcomeTemplate={data.settings.tpl_welcome}
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
                save.mutate(() => deleteRow("clients", deleting.id, uid), {
                  onSuccess: () => setDeleting(null),
                })
              }
            >
              {save.isPending ? "Eliminando…" : "Eliminar cliente"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}

function phoneFromPaste(text: string): string | null {
  const value = text.trim();
  if (!/wa\.me|whatsapp\.com|api\.whatsapp/i.test(value)) return null;
  const param = value.match(/[?&]phone=(\d+)/i)?.[1];
  const path = value.match(/wa\.me\/\+?(\d+)/i)?.[1];
  const digits = param ?? path;
  return digits && digits.length >= 10 ? `+${digits}` : null;
}

function ClientForm({
  client,
  uid,
  welcomeTemplate,
  close,
}: {
  client?: Client;
  uid: string;
  welcomeTemplate: string;
  close: () => void;
}) {
  const save = useSave(uid);
  const [id] = useState(() => client?.id ?? crypto.randomUUID());
  const [phone, setPhone] = useState(client?.phone ?? "");
  const [sendWelcome, setSendWelcome] = useState(false);
  const [welcomeLink, setWelcomeLink] = useState<string | null>(null);
  const checkLink = whatsapp(phone, "", "");
  if (welcomeLink)
    return (
      <Modal title="Cliente guardado" close={close}>
        <p>Listo. Abre WhatsApp para mandar la bienvenida.</p>
        <div className="form-actions">
          <button className="secondary" type="button" onClick={close}>
            Cerrar
          </button>
          <a
            className="primary"
            href={welcomeLink}
            target="_blank"
            rel="noreferrer"
            onClick={close}
          >
            <MessageCircle size={18} />
            Abrir WhatsApp
          </a>
        </div>
      </Modal>
    );
  return (
    <Modal
      title={client ? "Editar cliente" : "Un nuevo compañero"}
      close={close}
      busy={save.isPending}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          const row: Client = {
            id,
            user_id: uid,
            owner_name: String(form.get("owner")).trim(),
            pet_name: String(form.get("pet")).trim(),
            breed: String(form.get("breed")).trim(),
            phone: phone.trim(),
            pet_type: form.get("type") as Client["pet_type"],
            last_visit: String(form.get("visit")),
            needs_reschedule: client?.needs_reschedule ?? false,
            updated_at: new Date().toISOString(),
          };
          if (!row.owner_name || !row.pet_name) return;
          const welcome =
            sendWelcome && !client
              ? whatsapp(row.phone, welcomeTemplate, row.owner_name)
              : null;
          save.mutate(() => saveRow("clients", row), {
            onSuccess: () => (welcome ? setWelcomeLink(welcome) : close()),
          });
        }}
      >
        <fieldset disabled={save.isPending}>
          <Field label="Nombre de la persona">
            <input
              name="owner"
              defaultValue={client?.owner_name}
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
                defaultValue={client?.pet_name}
                required
                maxLength={120}
                pattern=".*\S.*"
              />
            </Field>
            <Field label="Tipo">
              <select name="type" defaultValue={client?.pet_type ?? "DOG"}>
                <option value="DOG">Perro</option>
                <option value="CAT">Gato</option>
              </select>
            </Field>
          </div>
          <Field label="Raza">
            <input name="breed" defaultValue={client?.breed} maxLength={120} />
          </Field>
          <Field label="Teléfono / WhatsApp">
            <input
              name="phone"
              type="tel"
              autoComplete="tel"
              placeholder="10 dígitos, código internacional o enlace de WhatsApp"
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              onPaste={(event) => {
                const parsed = phoneFromPaste(
                  event.clipboardData.getData("text"),
                );
                if (parsed) {
                  event.preventDefault();
                  setPhone(parsed);
                }
              }}
              pattern="[+0-9 ()\-]{10,25}"
            />
          </Field>
          {checkLink && (
            <a
              className="text-button"
              href={checkLink}
              target="_blank"
              rel="noreferrer"
            >
              <MessageCircle size={17} />
              Verificar número en WhatsApp
            </a>
          )}
          {!client && checkLink && (
            <label className="check-row">
              <input
                type="checkbox"
                checked={sendWelcome}
                onChange={(event) => setSendWelcome(event.target.checked)}
              />
              Enviar mensaje de bienvenida por WhatsApp al guardar
            </label>
          )}
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
              {save.isPending ? "Guardando…" : "Guardar cliente"}
            </button>
          </div>
        </fieldset>
      </form>
    </Modal>
  );
}
