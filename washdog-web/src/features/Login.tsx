import { useState, type FormEvent } from 'react'
import { ArrowRight, CalendarDays, PawPrint, Wallet } from 'lucide-react'
import { Brand, Field, InstallApp } from '../components'
import { supabase } from '../data/supabase'

export function Login({ sessionError }: { sessionError: string }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(sessionError)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!supabase) return
    setBusy(true)
    setError('')
    const form = new FormData(event.currentTarget)
    try {
      const { error } = await supabase.auth.signInWithPassword({
        email: String(form.get('email')).trim(),
        password: String(form.get('password')),
      })
      if (error)
        setError(
          error.status === 400
            ? 'Revisa tu correo y contraseña.'
            : 'No pudimos iniciar sesión. Revisa tu conexión e inténtalo de nuevo.',
        )
    } catch {
      setError('No pudimos conectar. Revisa tu conexión e inténtalo de nuevo.')
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="login-layout">
      <section className="login-intro">
        <Brand />
        <div className="login-story">
          <div className="paw-art" aria-hidden="true">
            <PawPrint strokeWidth={1} />
          </div>
          <p className="eyebrow">MÁS CUIDADO. MENOS PENDIENTES.</p>
          <h1>
            Tu negocio.
            <br />
            Sus colitas felices.
          </h1>
          <p className="intro-copy">
            Todo lo que necesitas para cuidar de tu estética canina, estés donde
            estés.
          </p>
          <div className="login-features">
            <span>
              <CalendarDays /> Agenda al día
            </span>
            <span>
              <PawPrint /> Clientes cerca
            </span>
            <span>
              <Wallet /> Cuentas claras
            </span>
          </div>
        </div>
        <p className="intro-footer">Hecho para quienes cuidan de ellos.</p>
      </section>
      <section className="login-form-section">
        <div className="login-form-wrap">
          <span className="small-logo">
            <PawPrint size={30} />
          </span>
          <h2>Qué bueno verte.</h2>
          <p>Entra a tu espacio Washdog.</p>
          {!supabase && (
            <p className="notice" role="status">
              La app todavía no está conectada. Contacta al administrador para
              habilitar el acceso.
            </p>
          )}
          <form onSubmit={(event) => void submit(event)}>
            <fieldset disabled={busy || !supabase}>
              <Field label="Correo electrónico">
                <input
                  name="email"
                  type="email"
                  autoComplete="username"
                  placeholder="tu@correo.com"
                  required
                />
              </Field>
              <Field label="Contraseña">
                <input
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  placeholder="Tu contraseña"
                  required
                />
              </Field>
              {error && (
                <p role="alert" className="error">
                  {error}
                </p>
              )}
              <button className="primary full" type="submit">
                {busy ? 'Entrando…' : 'Entrar a Washdog'}
                <ArrowRight size={19} />
              </button>
            </fieldset>
          </form>
          <p className="login-existing">
            Usa la misma cuenta de tu app Android.
          </p>
          <div className="login-install">
            <p>Siempre a la mano, también en iPhone.</p>
            <InstallApp />
          </div>
        </div>
        <p className="login-footnote">
          Un solo espacio. En todos tus dispositivos.
        </p>
      </section>
    </div>
  )
}
