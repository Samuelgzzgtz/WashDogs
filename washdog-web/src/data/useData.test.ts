import { beforeEach, expect, it, vi } from 'vitest'

const mock = vi.hoisted(() => ({
  rows: [] as { id: string }[],
  pageLimit: 100,
  fail: false,
  writeCount: 1,
}))
vi.mock('./supabase', () => ({
  supabase: {
    from: () => {
      const builder = {
        select: () => builder,
        filter: () => builder,
        order: () => builder,
        upsert: () => ({
          select: async () => ({
            data: Array.from({ length: mock.writeCount }, () => ({})),
            error: mock.fail ? new Error('Backend unavailable') : null,
          }),
        }),
        range: (start: number, end: number) => ({
          overrideTypes: async () => ({
            data: mock.rows.slice(
              start,
              Math.min(end + 1, start + mock.pageLimit),
            ),
            count: mock.rows.length,
            error: mock.fail ? new Error('Backend unavailable') : null,
          }),
        }),
      }
      return builder
    },
  },
}))
import { loadRows, saveRow } from './useData'
import { defaultSettings } from './types'

beforeEach(() => {
  mock.rows = []
  mock.fail = false
  mock.writeCount = 1
})
it('recupera todos los registros aunque el límite del servidor sea menor al tamaño solicitado', async () => {
  mock.rows = Array.from({ length: 321 }, (_, index) => ({ id: String(index) }))
  expect(await loadRows('clients', 'owner')).toEqual(mock.rows)
})
it('propaga errores de lectura en vez de mostrar un negocio vacío', async () => {
  mock.fail = true
  await expect(loadRows('clients', 'owner')).rejects.toThrow(
    'Backend unavailable',
  )
})
it('rechaza un guardado ignorado por el servidor en vez de cerrar el formulario como exitoso', async () => {
  mock.writeCount = 0
  await expect(
    saveRow('user_settings', defaultSettings('owner')),
  ).rejects.toThrow('WASHDOG_STALE')
  mock.writeCount = 1
  await expect(
    saveRow('user_settings', defaultSettings('owner')),
  ).resolves.toBeUndefined()
})
