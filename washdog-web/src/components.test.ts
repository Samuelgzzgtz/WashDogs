import { afterEach, describe, expect, it, vi } from 'vitest'
import { applyAppUpdate } from './components'

vi.mock('virtual:pwa-register/react', () => ({ useRegisterSW: vi.fn() }))

afterEach(() => vi.unstubAllGlobals())

describe('actualizar la app instalada', () => {
  it('espera el cambio de controlador aunque Workbox no recargue una actualización externa', async () => {
    const reload = vi.fn()
    const serviceWorker = Object.assign(new EventTarget(), {
      getRegistration: vi.fn().mockResolvedValue({ waiting: {} }),
    })
    vi.stubGlobal('navigator', { serviceWorker })
    vi.stubGlobal('window', { location: { reload } })
    const activate = vi.fn().mockResolvedValue(undefined)

    await applyAppUpdate(activate)
    expect(activate).toHaveBeenCalledWith(true)
    expect(reload).not.toHaveBeenCalled()

    serviceWorker.dispatchEvent(new Event('controllerchange'))
    serviceWorker.dispatchEvent(new Event('controllerchange'))
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('recarga si otro proceso ya activó la actualización', async () => {
    const reload = vi.fn()
    vi.stubGlobal('navigator', {
      serviceWorker: { getRegistration: vi.fn().mockResolvedValue({ waiting: null }) },
    })
    vi.stubGlobal('window', { location: { reload } })
    const activate = vi.fn()

    await applyAppUpdate(activate)
    expect(reload).toHaveBeenCalledTimes(1)
    expect(activate).not.toHaveBeenCalled()
  })
})
