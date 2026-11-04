import type { Appointment, Client } from "./data/types";
import { addDays } from "./domain";

/** Un horario de atención: hora de llegada y de salida en formato 24 h ("15:30"). */
export type Slot = { time: string; end: string };

function addMinutes(time: string, minutes: number): string {
  const total = Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5)) + minutes;
  return `${String(Math.floor(total / 60) % 24).padStart(2, "0")}:${String(total % 60).padStart(2, "0")}`;
}
const slot = (time: string, minutes: number): Slot => ({
  time,
  end: addMinutes(time, minutes),
});

const weekday = [
  slot("15:30", 90),
  slot("17:00", 90),
  slot("18:30", 90),
];

/**
 * Horarios de siempre (calendario semanal de Washdog), por día de la semana
 * según Date.getDay(): 0 domingo … 6 sábado. Para cambiar los horarios basta editar esto.
 */
export const weeklySlots: Record<number, Slot[]> = {
  0: [],
  1: weekday,
  2: weekday,
  3: [slot("15:30", 90), slot("17:00", 90), slot("19:00", 90)],
  4: [slot("15:30", 90), slot("17:00", 90)],
  5: weekday,
  6: [
    slot("10:00", 120),
    slot("12:00", 120),
    slot("14:00", 120),
    slot("16:00", 120),
  ],
};

const dayOfWeek = (date: string) => new Date(`${date}T12:00:00`).getDay();

export const slotsForDate = (date: string): Slot[] =>
  weeklySlots[dayOfWeek(date)] ?? [];

/** Lunes de la semana que contiene la fecha (las semanas empiezan en lunes). */
export const mondayOf = (date: string): string =>
  addDays(date, -((dayOfWeek(date) + 6) % 7));

/** "15:30" → "3:30pm", "10:00" → "10:00am", "12:00" → "12:00pm". */
export function slotLabel(time: string): string {
  const hour = Number(time.slice(0, 2));
  const suffix = hour >= 12 ? "pm" : "am";
  return `${hour % 12 || 12}:${time.slice(3, 5)}${suffix}`;
}

/** Hora de salida de una cita; si no la tiene se asume una hora, igual que al detectar choques. */
const endOf = (a: Appointment): string =>
  a.end_time ||
  `${String(Number(a.time.slice(0, 2)) + 1).padStart(2, "0")}${a.time.slice(2)}`;
const overlaps = (a: Appointment, s: Slot): boolean =>
  s.time < endOf(a) && s.end > a.time;

export type SlotCell = {
  date: string;
  slot: Slot;
  /** Cita que ocupa el horario; sin ella el horario está libre. */
  appointment?: Appointment;
  /** El horario ya pasó. */
  past: boolean;
};
export type DaySchedule = {
  date: string;
  cells: SlotCell[];
  /** Citas del día que no caen en ningún horario de la tabla. */
  extras: Appointment[];
};

/** Los siete días de la semana (lunes a domingo) con sus horarios libres y ocupados. */
export function weekSchedule(
  weekStart: string,
  appointments: Appointment[],
  now = new Date(),
): DaySchedule[] {
  return Array.from({ length: 7 }, (_, i) => {
    const date = addDays(weekStart, i);
    const day = appointments
      .filter((a) => a.date === date)
      .sort((a, b) => a.time.localeCompare(b.time));
    const slots = slotsForDate(date);
    return {
      date,
      cells: slots.map((s) => ({
        date,
        slot: s,
        appointment: day.find((a) => overlaps(a, s)),
        past: new Date(`${date}T${s.time}:00`).getTime() < now.getTime(),
      })),
      extras: day.filter((a) => !slots.some((s) => overlaps(a, s))),
    };
  });
}

/** Cita para el cliente en el horario elegido; dirección y "nervioso" van en las notas para tenerlos a la mano. */
export function appointmentForSlot(
  client: Client,
  picked: { date: string; slot: Slot },
  uid: string,
  id: string,
  now = new Date(),
): Appointment {
  const notes = [
    client.address && `Dirección: ${client.address}`,
    client.nervous && "Es nervioso",
  ]
    .filter(Boolean)
    .join(" · ");
  return {
    id,
    user_id: uid,
    client_id: client.id,
    client_name: client.owner_name,
    pet_name: client.pet_name,
    service: "Baño",
    time: picked.slot.time,
    end_time: picked.slot.end,
    date: picked.date,
    status: "SCHEDULED",
    notes,
    repeat_every_days: null,
    series_id: null,
    updated_at: now.toISOString(),
  };
}
