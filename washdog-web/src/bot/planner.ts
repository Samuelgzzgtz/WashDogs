import type { Appointment, Client } from "../data/types";
import { addDays } from "../domain";
import { slotsForDate } from "../schedule";
import type { BotConfig, PetKind, PriceTime, Size, Zone } from "./config";

const toMinutes = (time: string) =>
  Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
const toTime = (minutes: number) =>
  `${String(Math.floor(minutes / 60) % 24).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
const normalize = (text: string) =>
  text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "");

/* ---------- Zonas ---------- */

/** Zona de una dirección: la primera cuya palabra clave aparece; si ninguna, la zona por defecto. */
export function zoneFor(address: string, config: BotConfig): Zone {
  const text = normalize(address);
  const found =
    text &&
    config.zones.find((zone) =>
      zone.keywords.some((keyword) => {
        const k = normalize(keyword).trim();
        return k && text.includes(k);
      }),
    );
  return (
    found ||
    config.zones.find((zone) => zone.id === config.defaultZoneId) ||
    config.zones[0]
  );
}

/** Dirección de una cita: la del cliente, o la que quedó en las notas ("Dirección: …"). */
export function appointmentAddress(a: Appointment, clients: Client[]): string {
  const client = clients.find((c) => c.id === a.client_id);
  if (client?.address) return client.address;
  return /Dirección:\s*([^·]+)/.exec(a.notes ?? "")?.[1]?.trim() ?? "";
}

/**
 * Minutos aproximados de traslado entre dos zonas. Misma zona: el valor fijo de la
 * configuración. Distinta zona: el mayor de los dos traslados desde la base, porque
 * casi todo queda alrededor de Mitras Poniente. Es una estimación hasta conectar mapas.
 */
export function travelMinutes(a: Zone, b: Zone, config: BotConfig): number {
  if (a.id === b.id) return config.rules.sameZoneTravelMinutes;
  return Math.max(a.travelMinutes, b.travelMinutes);
}

/* ---------- Precio y duración ---------- */

export type Pet = {
  kind: PetKind;
  /** ids de extras planos o por talla (ej. "unas", "deslanado"). */
  extras?: string[];
  /** Trae muchos nudos: se pide foto y revisión humana. */
  matted?: boolean;
};
export type QuoteLine = { label: string; price: number; from?: boolean };
export type Quote = {
  price: number;
  /** Algún precio es "desde": el total puede subir. */
  from: boolean;
  minutes: number;
  lines: QuoteLine[];
};

const sizeOf = (kind: PetKind): Size | null =>
  kind === "GATO_CORTO" || kind === "GATO_LARGO" ? null : kind;

export function petKindOf(client: Pick<Client, "pet_type" | "size" | "coat">): PetKind | null {
  if (client.pet_type === "CAT") return client.coat === "LARGO" ? "GATO_LARGO" : "GATO_CORTO";
  return (client.size || null) as PetKind | null;
}

/** Precio y tiempo de una cita con uno o varios animales, más el domicilio. */
export function quote(pets: Pet[], zone: Zone, config: BotConfig): Quote {
  const lines: QuoteLine[] = [];
  let minutes = 0;
  const add = (label: string, item: PriceTime) => {
    lines.push(item.from ? { label, price: item.price, from: true } : { label, price: item.price });
    minutes += item.minutes;
  };
  for (const pet of pets) {
    add(`Baño ${labelOf(pet.kind)}`, config.baths[pet.kind]);
    for (const id of pet.extras ?? []) {
      const flat = config.extras.find((e) => e.id === id);
      if (flat) add(flat.name, flat);
      const sized = config.sizedExtras.find((e) => e.id === id);
      const size = sizeOf(pet.kind);
      if (sized && size) add(`${sized.name} (${labelOf(pet.kind)})`, sized.bySize[size]);
    }
  }
  if (zone.mode === "NORMAL")
    lines.push(
      zone.feeFrom
        ? { label: `Domicilio · ${zone.name}`, price: zone.fee, from: true }
        : { label: `Domicilio · ${zone.name}`, price: zone.fee },
    );
  return {
    price: lines.reduce((sum, l) => sum + l.price, 0),
    from: lines.some((l) => l.from),
    minutes,
    lines,
  };
}
const labelOf = (kind: PetKind) =>
  ({
    PEQUENO: "perro pequeño",
    MEDIANO: "perro mediano",
    GRANDE: "perro grande",
    JUMBO: "perro jumbo",
    GATO_CORTO: "gato pelo corto",
    GATO_LARGO: "gato pelo largo",
  })[kind];

/** Casos que el bot no calcula solo: varios perros grandes, o nudos. Pide foto y avisa. */
export function needsReview(pets: Pet[], config: BotConfig): boolean {
  const large = pets.filter((p) => p.kind === "GRANDE" || p.kind === "JUMBO").length;
  return pets.some((p) => p.matted) || large >= config.rules.reviewLargeDogs;
}

/* ---------- Horarios libres ---------- */

type Busy = { start: number; end: number; zone: Zone };

function busyBlocks(
  date: string,
  appointments: Appointment[],
  clients: Client[],
  config: BotConfig,
): Busy[] {
  return appointments
    .filter((a) => a.date === date && a.time)
    .map((a) => {
      const start = toMinutes(a.time);
      return {
        start,
        end: a.end_time ? toMinutes(a.end_time) : start + 60,
        zone: zoneFor(appointmentAddress(a, clients), config),
      };
    })
    .sort((a, b) => a.start - b.start);
}

export type Offer = {
  date: string;
  time: string;
  end: string;
  /** Minutos de manejo que agrega esta cita al día. Menos es mejor. */
  extraTravel: number;
  /** Ese día ya hay otra cita en la misma zona. */
  sameZone: boolean;
};

/**
 * Horarios de la tabla semanal en los que cabe un servicio de `minutes` en `zone`,
 * contando el traslado y el colchón antes y después de las citas que ya hay.
 * Nunca ofrece horarios del día de hoy ni pasados: eso lo decide una persona.
 */
export function offersForDate(
  date: string,
  minutes: number,
  zone: Zone,
  appointments: Appointment[],
  clients: Client[],
  config: BotConfig,
  now = new Date(),
): Offer[] {
  const today = isoLocal(now);
  if (date <= today || zone.mode !== "NORMAL") return [];
  const busy = busyBlocks(date, appointments, clients, config);
  const margin = config.rules.marginMinutes;
  const latest = toMinutes(config.rules.latestFinish);
  const sameZone = busy.some((b) => b.zone.id === zone.id);
  const offers: Offer[] = [];
  for (const slot of slotsForDate(date)) {
    const start = toMinutes(slot.time);
    const end = start + minutes;
    if (end > latest) continue;
    if (busy.some((b) => start < b.end && end > b.start)) continue;
    const prev = [...busy].reverse().find((b) => b.end <= start);
    const next = busy.find((b) => b.start >= end);
    const toHere = prev ? travelMinutes(prev.zone, zone, config) : 0;
    const fromHere = next ? travelMinutes(zone, next.zone, config) : 0;
    if (prev && prev.end + toHere + margin > start) continue;
    if (next && end + fromHere + margin > next.start) continue;
    const saved = prev && next ? travelMinutes(prev.zone, next.zone, config) : 0;
    offers.push({
      date,
      time: slot.time,
      end: toTime(end),
      extraTravel: toHere + fromHere - saved,
      sameZone,
    });
  }
  return offers;
}

/**
 * Mejores horarios de los próximos `days` días: primero los días donde ya hay
 * citas en la misma zona, luego los que agregan menos manejo, luego los más pronto.
 */
export function suggestOffers(
  minutes: number,
  zone: Zone,
  appointments: Appointment[],
  clients: Client[],
  config: BotConfig,
  { now = new Date(), days = 7, limit = 6 } = {},
): Offer[] {
  const today = isoLocal(now);
  const all: Offer[] = [];
  for (let i = 1; i <= days; i++)
    all.push(
      ...offersForDate(addDays(today, i), minutes, zone, appointments, clients, config, now),
    );
  return all
    .sort(
      (a, b) =>
        Number(b.sameZone) - Number(a.sameZone) ||
        a.extraTravel - b.extraTravel ||
        a.date.localeCompare(b.date) ||
        a.time.localeCompare(b.time),
    )
    .slice(0, limit)
    .sort((a, b) => a.date.localeCompare(b.date) || a.time.localeCompare(b.time));
}

const isoLocal = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
