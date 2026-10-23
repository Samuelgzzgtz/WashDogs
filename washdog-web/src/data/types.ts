export type Client = {
  id: string
  user_id: string
  owner_name: string
  pet_name: string
  breed: string
  last_visit: string
  needs_reschedule: boolean
  pet_type: 'DOG' | 'CAT'
  phone: string
  updated_at: string
}
export type Appointment = {
  id: string
  user_id: string
  client_id: string
  client_name: string
  pet_name: string
  service: string
  time: string
  end_time: string
  date: string
  status: 'DONE' | 'NEXT' | 'REMINDER' | 'SCHEDULED'
  notes: string
  repeat_every_days: number | null
  series_id: string | null
  updated_at: string
}
export type Transaction = {
  id: string
  user_id: string
  type: 'IN' | 'OUT'
  amount: number
  concept: string
  detail: string
  date: string
  updated_at: string
}
export type Settings = {
  user_id: string
  tpl_welcome: string
  tpl_confirm: string
  tpl_reschedule: string
  updated_at: string
}
export type Tombstone = {
  user_id: string
  entity: string
  entity_id: string
  deleted_at: string
}
export type AppointmentSeries = {
  id: string
  user_id: string
  template: Appointment
  stopped_at: string
}
type Table<Row> = {
  Row: Row
  Insert: Row
  Update: Partial<Row>
  Relationships: []
}
export type Database = {
  public: {
    Tables: {
      clients: Table<Client>
      appointments: Table<Appointment>
      transactions: Table<Transaction>
      user_settings: Table<Settings>
      tombstones: Table<Tombstone>
      appointment_series: Table<AppointmentSeries>
    }
    Views: { [_ in never]: never }
    Functions: {
      washdog_stop_series: {
        Args: { p_series_id: string; p_today: string }
        Returns: undefined
      }
    }
    Enums: { [_ in never]: never }
    CompositeTypes: { [_ in never]: never }
  }
}
export type Snapshot = {
  clients: Client[]
  appointments: Appointment[]
  transactions: Transaction[]
  settings: Settings
}
export const defaultSettings = (user_id: string): Settings => ({
  user_id,
  updated_at: '',
  tpl_welcome:
    'Hola {{nombre}}, bienvenido/a. Estamos felices de tenerte con nosotros. 😊',
  tpl_confirm:
    'Hola {{nombre}}, confirmamos tu cita para el {{fecha}} a las {{hora}}. ¡Te esperamos!',
  tpl_reschedule:
    'Hola {{nombre}}, queremos coordinar una nueva fecha para tu cita. ¿Cuándo te queda bien?',
})
