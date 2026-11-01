import {
  useEffect,
  useRef,
  useState,
  type InputHTMLAttributes,
  type ReactNode,
} from 'react'
import { ArrowDownToLine, Check, Share2, X } from 'lucide-react'
import { useRegisterSW } from 'virtual:pwa-register/react'

export function Brand() {
  return (
    <a className="brand" href="#inicio" aria-label="Washdog, inicio">
      <img src="/icons/icon-192.png" alt="" width="44" height="44" />
      <span>
        washdog<span className="brand-dot">.</span>
      </span>
    </a>
  )
}
export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}
export function Field({
  label,
  children,
}: {
  label: string
  children: ReactNode
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  )
}
/** Campo numérico de dinero con el signo de pesos siempre visible. */
export function MoneyInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className="money-input">
      <span aria-hidden="true">$</span>
      <input type="number" inputMode="decimal" step="0.01" {...props} />
    </div>
  )
}
export function Modal({
  title,
  close,
  children,
  busy = false,
}: {
  title: string
  close: () => void
  children: ReactNode
  busy?: boolean
}) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    ref.current?.showModal()
  }, [])
  return (
    <dialog
      ref={ref}
      className="modal"
      onCancel={(event) => {
        event.preventDefault()
        if (!busy) close()
      }}
    >
      <div className="modal-head">
        <h2>{title}</h2>
        <button
          type="button"
          className="icon-button"
          aria-label="Cerrar"
          disabled={busy}
          onClick={close}
        >
          <X size={22} />
        </button>
      </div>
      {children}
    </dialog>
  )
}

interface InstallPrompt extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}
export function InstallApp() {
  const [prompt, setPrompt] = useState<InstallPrompt | null>(null)
  const [help, setHelp] = useState(false)
  const [installed, setInstalled] = useState(
    window.matchMedia('(display-mode: standalone)').matches ||
      Boolean((navigator as Navigator & { standalone?: boolean }).standalone),
  )
  const [copied, setCopied] = useState(false)
  const ios =
    /iPhone|iPad|iPod/.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
  useEffect(() => {
    const onPrompt = (event: Event) => {
      event.preventDefault()
      setPrompt(event as InstallPrompt)
    }
    const onInstalled = () => {
      setInstalled(true)
      setPrompt(null)
    }
    window.addEventListener('beforeinstallprompt', onPrompt)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])
  async function install() {
    if (!prompt) {
      setHelp(true)
      return
    }
    await prompt.prompt()
    await prompt.userChoice
    setPrompt(null)
  }
  async function share() {
    const url = window.location.origin
    try {
      if (navigator.share)
        await navigator.share({
          title: 'Washdog',
          text: 'Tu estética canina, siempre contigo.',
          url,
        })
      else {
        await navigator.clipboard.writeText(url)
        setCopied(true)
      }
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError'))
        setHelp(true)
    }
  }
  return (
    <>
      <div className="install-actions">
        {!installed && (
          <button className="text-button" onClick={() => void install()}>
            <ArrowDownToLine size={17} /> Instalar app
          </button>
        )}
        <button className="text-button" onClick={() => void share()}>
          {copied ? <Check size={17} /> : <Share2 size={17} />}
          {copied ? 'Enlace copiado' : 'Compartir app'}
        </button>
      </div>
      {help && (
        <Modal title="Washdog en tu celular" close={() => setHelp(false)}>
          <p>
            {ios
              ? 'Abre este enlace en Safari. Toca Compartir y después “Añadir a pantalla de inicio”. Activa “Abrir como app” si aparece.'
              : 'Abre el menú de tu navegador y elige “Instalar aplicación” o “Añadir a pantalla de inicio”. En Android recomendamos Chrome.'}
          </p>
          <p>
            El icono aparecerá junto a tus otras apps. Comparte este enlace para
            abrir Washdog desde cualquier dispositivo:
          </p>
          <input
            aria-label="Enlace de la app"
            readOnly
            value={window.location.origin}
            onFocus={(event) => event.target.select()}
          />
          <button className="primary" onClick={() => setHelp(false)}>
            Entendido
          </button>
        </Modal>
      )}
    </>
  )
}

export async function applyAppUpdate(
  updateServiceWorker: (reloadPage?: boolean) => Promise<void>,
) {
  const registration = await navigator.serviceWorker.getRegistration()
  if (!registration?.waiting) {
    window.location.reload()
    return
  }
  // Workbox may classify an update from another tab as external and skip its reload.
  navigator.serviceWorker.addEventListener(
    'controllerchange',
    () => window.location.reload(),
    { once: true },
  )
  await updateServiceWorker(true)
}

export function AppUpdate() {
  const {
    needRefresh: [refresh],
    updateServiceWorker,
  } = useRegisterSW()
  return (
    refresh && (
      <aside className="update-notice" role="status">
        <span>Hay una nueva versión de Washdog.</span>
        <button onClick={() => void applyAppUpdate(updateServiceWorker)}>
          Actualizar
        </button>
      </aside>
    )
  )
}

export function useOnline() {
  const [online, setOnline] = useState(navigator.onLine)
  useEffect(() => {
    const update = () => setOnline(navigator.onLine)
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    return () => {
      window.removeEventListener('online', update)
      window.removeEventListener('offline', update)
    }
  }, [])
  return online
}
