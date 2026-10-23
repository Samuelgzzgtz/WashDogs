import { useState } from 'react'
import { LogOut } from 'lucide-react'
import { Field, InstallApp, Modal } from '../components'
import type { Settings as UserSettings } from '../data/types'
import { errorMessage, saveRow, useSave } from '../data/useData'

export function Settings({
  settings,
  email,
  logout,
}: {
  settings: UserSettings
  email: string
  logout: () => Promise<void>
}) {
  const save = useSave(settings.user_id)
  const [saved, setSaved] = useState(false)
  const [confirmLogout, setConfirmLogout] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [busy, setBusy] = useState(false)
  return (
    <>
      <div className="page-heading">
        <div>
          <h1>A tu manera</h1>
          <p>Los pequeños detalles de tu espacio.</p>
        </div>
      </div>
      <section className="settings-section">
        <h2>Mensajes de WhatsApp</h2>
        <p>
          Personaliza tus mensajes. Usa {'{{nombre}}'}, {'{{fecha}}'} y{' '}
          {'{{hora}}'} para completar los datos.
        </p>
        <form
          onSubmit={(event) => {
            event.preventDefault()
            setSaved(false)
            const form = new FormData(event.currentTarget)
            save.mutate(
              () =>
                saveRow('user_settings', {
                  ...settings,
                  tpl_welcome: String(form.get('welcome')),
                  tpl_confirm: String(form.get('confirm')),
                  tpl_reschedule: String(form.get('reschedule')),
                  updated_at: new Date().toISOString(),
                }),
              { onSuccess: () => setSaved(true) },
            )
          }}
        >
          <fieldset disabled={save.isPending}>
            <Field label="Bienvenida">
              <textarea
                name="welcome"
                rows={3}
                required
                maxLength={3000}
                defaultValue={settings.tpl_welcome}
                onChange={() => setSaved(false)}
              />
            </Field>
            <Field label="Confirmación de cita">
              <textarea
                name="confirm"
                rows={3}
                required
                maxLength={3000}
                defaultValue={settings.tpl_confirm}
                onChange={() => setSaved(false)}
              />
            </Field>
            <Field label="Nueva visita">
              <textarea
                name="reschedule"
                rows={3}
                required
                maxLength={3000}
                defaultValue={settings.tpl_reschedule}
                onChange={() => setSaved(false)}
              />
            </Field>
            {save.error && (
              <p className="error" role="alert">
                {errorMessage(save.error)}
              </p>
            )}
            {saved && (
              <p className="success" role="status">
                Mensajes guardados.
              </p>
            )}
            <button className="primary" type="submit">
              {save.isPending ? 'Guardando…' : 'Guardar mensajes'}
            </button>
          </fieldset>
        </form>
      </section>
      <section className="settings-section">
        <h2>Washdog, siempre contigo</h2>
        <p>
          Instala la app en tu pantalla de inicio o comparte el enlace. Cada
          persona entra con su propia cuenta.
        </p>
        <InstallApp />
      </section>
      <section className="settings-section">
        <h2>Tu cuenta</h2>
        <p>{email}</p>
        <button
          className="text-button danger"
          onClick={() => setConfirmLogout(true)}
        >
          <LogOut size={18} />
          Cerrar sesión
        </button>
      </section>
      {confirmLogout && (
        <Modal
          title="¿Cerrar sesión?"
          close={() => setConfirmLogout(false)}
          busy={busy}
        >
          <p>Tu información seguirá guardada en la nube.</p>
          {logoutError && (
            <p className="error" role="alert">
              {logoutError}
            </p>
          )}
          <div className="form-actions">
            <button
              className="secondary"
              disabled={busy}
              onClick={() => setConfirmLogout(false)}
            >
              Cancelar
            </button>
            <button
              className="primary"
              disabled={busy}
              onClick={() => {
                setBusy(true)
                void logout().catch(() => {
                  setLogoutError(
                    'No pudimos cerrar la sesión. Inténtalo de nuevo.',
                  )
                  setBusy(false)
                })
              }}
            >
              {busy ? 'Cerrando…' : 'Cerrar sesión'}
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}
