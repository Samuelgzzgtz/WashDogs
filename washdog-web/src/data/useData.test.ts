import { beforeEach, expect, it, vi } from 'vitest'

const mock = vi.hoisted(() => ({
  rows: [] as { id: string }[],
  pageLimit: 100,
  fail: false,
}))
vi.mock('./supabase', () => ({
  supabase: {
    from: () => {
      const builder = {
        select: () => builder,
        filter: () => builder,
        order: () => builder,
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
import { loadRows } from './useData'

beforeEach(() => {
  mock.rows = []
  mock.fail = false
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
