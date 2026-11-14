export type Client = {
  id: string;
  user_id: string;
  owner_name: string;
  pet_name: string;
  breed: string;
  last_visit: string;
  needs_reschedule: boolean;
  pet_type: "DOG" | "CAT";
  phone: string;
  address: string;
  nervous: boolean;
  /** Talla del perro, para calcular cuánto dura el servicio. Vacío = sin capturar. */
  size: "" | "PEQUENO" | "MEDIANO" | "GRANDE" | "JUMBO";
  /** Tipo de pelo (sobre todo para gatos). Vacío = sin capturar. */
  coat: "" | "CORTO" | "LARGO";
  updated_at: string;
};
export type Appointment = {
  id: string;
  user_id: string;
  client_id: string;
  client_name: string;
  pet_name: string;
  service: string;
  time: string;
  end_time: string;
  date: string;
  status: "DONE" | "NEXT" | "REMINDER" | "SCHEDULED";
  notes: string;
  repeat_every_days: number | null;
  series_id: string | null;
  updated_at: string;
};
export type Transaction = {
  id: string;
  user_id: string;
  type: "IN" | "OUT";
  amount: number;
  concept: string;
  detail: string;
  date: string;
  updated_at: string;
};
export type Goal = {
  id: string;
  user_id: string;
  name: string;
  target_amount: number;
  current_amount: number;
  deadline: string | null;
  updated_at: string;
};
export type CoinCount = {
  id: string;
  user_id: string;
  denomination: number;
  quantity: number;
  updated_at: string;
};
export type Settings = {
  user_id: string;
  tpl_welcome: string;
  tpl_confirm: string;
  tpl_reschedule: string;
  updated_at: string;
};
export type Tombstone = {
  user_id: string;
  entity: string;
  entity_id: string;
  deleted_at: string;
};
export type AppointmentSeries = {
  id: string;
  user_id: string;
  template: Appointment;
  stopped_at: string;
};
export type BotConfigRow = {
  user_id: string;
  /** Ver `BotConfig` en src/bot/config.ts. */
  config: Record<string, unknown>;
  updated_at: string;
};
export type BotAlert = {
  id: string;
  user_id: string;
  kind: "BOOKING" | "SAME_DAY" | "OUT_OF_ZONE" | "REVIEW" | "TEST";
  phone: string;
  customer_name: string;
  summary: string;
  requested_date: string;
  requested_time: string;
  status: "PENDING" | "ACCEPTED" | "REJECTED";
  created_at: string;
  updated_at: string;
};
export type PushSubscriptionRow = {
  id: string;
  user_id: string;
  endpoint: string;
  p256dh: string;
  auth: string;
  user_agent: string;
  created_at: string;
};
type Table<Row> = {
  Row: Row;
  Insert: Row;
  Update: Partial<Row>;
  Relationships: [];
};
export type Database = {
  public: {
    Tables: {
      clients: Table<Client>;
      appointments: Table<Appointment>;
      transactions: Table<Transaction>;
      goals: Table<Goal>;
      coin_counts: Table<CoinCount>;
      user_settings: Table<Settings>;
      tombstones: Table<Tombstone>;
      appointment_series: Table<AppointmentSeries>;
      bot_config: Table<BotConfigRow>;
      bot_alerts: Table<BotAlert>;
      push_subscriptions: Table<PushSubscriptionRow>;
    };
    Views: { [_ in never]: never };
    Functions: {
      washdog_stop_series: {
        Args: { p_series_id: string; p_today: string };
        Returns: undefined;
      };
    };
    Enums: { [_ in never]: never };
    CompositeTypes: { [_ in never]: never };
  };
};
export type Snapshot = {
  clients: Client[];
  appointments: Appointment[];
  transactions: Transaction[];
  goals: Goal[];
  coinCounts: CoinCount[];
  settings: Settings;
};
export const defaultSettings = (user_id: string): Settings => ({
  user_id,
  updated_at: "",
  tpl_welcome:
    "Hola {{nombre}}, bienvenido/a. Estamos felices de tenerte con nosotros. 😊",
  tpl_confirm:
    "Hola {{nombre}}, confirmamos tu cita para el {{fecha}} a las {{hora}}. ¡Te esperamos!",
  tpl_reschedule:
    "Hola {{nombre}}, queremos coordinar una nueva fecha para tu cita. ¿Cuándo te queda bien?",
});
