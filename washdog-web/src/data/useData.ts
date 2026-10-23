import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { supabase } from './supabase'
import { defaultSettings, type Database, type Snapshot } from './types'
import { missingAppointments, visibleRows } from '../domain'

type TableName = keyof Database['public']['Tables']
export async function loadRows<T extends TableName>(
  table: T,
  uid: string,
): Promise<Database['public']['Tables'][T]['Row'][]> {
  if (!supabase) throw new Error('La app no está conectada.')
  const rows: Database['public']['Tables'][T]['Row'][] = []
  // PostgREST caps a response at 1,000 rows by default. Stable ordering avoids missing pages.
  const order =
    table === 'user_settings'
      ? 'user_id'
      : table === 'tombstones'
        ? 'entity_id'
        : 'id'
  for (let offset = 0; ;) {
    let query = supabase
      .from(table)
      .select('*', { count: 'exact' })
      .filter('user_id', 'eq', uid)
      .order(order)
    if (table === 'tombstones') query = query.order('entity')
    const { data, error, count } = await query
      .range(offset, offset + 499)
      .overrideTypes<
        Database['public']['Tables'][T]['Row'][],
        { merge: false }
      >()
    if (error) throw error
    rows.push(...data)
    if (!data.length || (count !== null && rows.length >= count)) return rows
    offset += data.length
  }
}

export async function loadSnapshot(uid: string): Promise<Snapshot> {
  const [clients, appointments, transactions, settings, tombstones, series] =
    await Promise.all([
      loadRows('clients', uid),
      loadRows('appointments', uid),
      loadRows('transactions', uid),
      loadRows('user_settings', uid),
      loadRows('tombstones', uid),
      loadRows('appointment_series', uid),
    ])
  let visibleAppointments = visibleRows(
    appointments,
    tombstones,
    'appointments',
  )
  const missing = missingAppointments(visibleAppointments, tombstones, series)
  if (missing.length) {
    const { error } = await supabase!
      .from('appointments')
      .upsert(missing, { ignoreDuplicates: true })
    if (error && !error.message.includes('WASHDOG_OVERLAP')) throw error
    // Another device may book a slot or generate the same occurrence while we load.
    // Read back the rows that actually exist, including when the batch hit a conflict.
    visibleAppointments = visibleRows(
      await loadRows('appointments', uid),
      tombstones,
      'appointments',
    )
  }
  return {
    clients: visibleRows(clients, tombstones, 'clients'),
    appointments: visibleAppointments,
    transactions: visibleRows(transactions, tombstones, 'transactions'),
    settings: settings[0] ?? defaultSettings(uid),
  }
}

export function useData(uid: string) {
  return useQuery({
    queryKey: ['washdog', uid],
    queryFn: () => loadSnapshot(uid),
    refetchInterval: 60_000,
  })
}

export function useSave(uid: string) {
  const client = useQueryClient()
  return useMutation({
    // Offline writes fail immediately, keeping the editable form instead of queuing a save.
    networkMode: 'always',
    mutationFn: async (action: () => Promise<void>) => {
      if (!navigator.onLine)
        throw new Error('Sin conexión. Reconéctate e inténtalo de nuevo.')
      await action()
    },
    onSuccess: () => client.invalidateQueries({ queryKey: ['washdog', uid] }),
  })
}

export async function saveRow<
  T extends 'clients' | 'appointments' | 'transactions' | 'user_settings',
>(table: T, row: Database['public']['Tables'][T]['Row']) {
  if (!supabase) throw new Error('La app no está conectada.')
  // Ownership is checked by RLS as well as the session-scoped queries.
  const { error } = await supabase.from(table).upsert(row as never)
  if (error) throw error
}

export async function deleteRow(
  table: 'clients' | 'appointments' | 'transactions',
  id: string,
  uid: string,
) {
  if (!supabase) throw new Error('La app no está conectada.')
  // Database triggers record tombstones and cascade client appointments in the same transaction.
  const { error } = await supabase
    .from(table)
    .delete()
    .eq('id', id)
    .eq('user_id', uid)
  if (error) throw error
}

export function errorMessage(error: unknown): string {
  const message =
    error instanceof Error
      ? error.message
      : typeof error === 'object' && error && 'message' in error
        ? String(error.message)
        : ''
  if (message.includes('WASHDOG_OVERLAP'))
    return 'Ese horario ya está ocupado. Elige otro horario.'
  if (message.includes('Sin conexión')) return message
  return 'No se pudo guardar en la nube. Tus cambios siguen en el formulario; vuelve a intentarlo.'
}
