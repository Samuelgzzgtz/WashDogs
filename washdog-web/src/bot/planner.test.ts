import { describe, expect, it } from "vitest";
import type { Appointment, Client } from "../data/types";
import { defaultBotConfig, withDefaults } from "./config";
import {
  appointmentAddress,
  needsReview,
  offersForDate,
  petKindOf,
  quote,
  suggestOffers,
  travelMinutes,
  zoneFor,
} from "./planner";

const config = defaultBotConfig();
const zone = (id: string) => config.zones.find((z) => z.id === id)!;
// Sábado 14 de noviembre de 2026; el lunes siguiente es el 16.
const now = new Date("2026-11-14T10:00:00");
const MONDAY = "2026-11-16";

const client = (id: string, address: string): Client => ({
  id,
  user_id: "u1",
  owner_name: id,
  pet_name: id,
  breed: "",
  last_visit: "",
  needs_reschedule: false,
  pet_type: "DOG",
  phone: "",
  address,
  nervous: false,
  size: "",
  coat: "",
  updated_at: "",
});
const appt = (
  id: string,
  clientId: string,
  time: string,
  end: string,
  date = MONDAY,
): Appointment => ({
  id,
  user_id: "u1",
  client_id: clientId,
  client_name: clientId,
  pet_name: clientId,
  service: "Baño",
  time,
  end_time: end,
  date,
  status: "SCHEDULED",
  notes: "",
  repeat_every_days: null,
  series_id: null,
  updated_at: "",
});

describe("zoneFor", () => {
  it("ubica la dirección por palabras clave, sin importar acentos ni mayúsculas", () => {
    expect(zoneFor("Monasterio 1421 Sector Granada", config).id).toBe("granada");
    expect(zoneFor("calera 716 sector jerez", config).id).toBe("mitras");
    expect(zoneFor("Dominio CUMBRES Topacio 109", config).id).toBe("cerca");
    expect(zoneFor("Col. Centro, San Jerónimo", config).id).toBe("monterrey");
    expect(zoneFor("Av. Sendero, Escobedo", config).id).toBe("fuera");
    expect(zoneFor("San Nicolás de los Garza", config).mode).toBe("ALERT");
  });
  it("Mitras Poniente gana aunque la dirección diga García", () => {
    expect(zoneFor("Mitras Poniente, García N.L.", config).id).toBe("mitras");
  });
  it("sin coincidencias usa la zona por defecto", () => {
    expect(zoneFor("Calle Rafael Sanzio 505", config).id).toBe("monterrey");
    expect(zoneFor("", config).id).toBe("monterrey");
  });
});

describe("travelMinutes", () => {
  it("misma zona usa el traslado corto; distinta zona el mayor desde la base", () => {
    expect(travelMinutes(zone("cerca"), zone("cerca"), config)).toBe(10);
    expect(travelMinutes(zone("granada"), zone("cerca"), config)).toBe(25);
  });
});

describe("quote", () => {
  it("suma baño, extras por talla y domicilio", () => {
    const q = quote(
      [{ kind: "GRANDE", extras: ["cepillado", "unas"] }],
      zone("cerca"),
      config,
    );
    expect(q.price).toBe(320 + 150 + 50 + 80);
    expect(q.minutes).toBe(120 + 60);
    expect(q.from).toBe(true); // domicilio "desde $80"
  });
  it("Sector Granada no cobra domicilio y suma varios perros", () => {
    const q = quote(
      [{ kind: "PEQUENO", extras: ["deslanado"] }, { kind: "MEDIANO" }],
      zone("granada"),
      config,
    );
    expect(q.price).toBe(220 + 80 + 250);
    expect(q.minutes).toBe(60 + 30 + 90);
    expect(q.from).toBe(false);
  });
  it("jumbo con deslanado llega a 3 horas y su precio es 'desde'", () => {
    const q = quote([{ kind: "JUMBO", extras: ["deslanado"] }], zone("granada"), config);
    expect(q.minutes).toBe(180);
    expect(q.from).toBe(true);
  });
});

describe("needsReview", () => {
  it("pide revisión con nudos o con dos perros grandes", () => {
    expect(needsReview([{ kind: "GRANDE" }], config)).toBe(false);
    expect(needsReview([{ kind: "PEQUENO", matted: true }], config)).toBe(true);
    expect(needsReview([{ kind: "GRANDE" }, { kind: "JUMBO" }], config)).toBe(true);
  });
});

describe("petKindOf", () => {
  it("usa la talla para perros y el pelo para gatos", () => {
    expect(petKindOf({ pet_type: "DOG", size: "GRANDE", coat: "" })).toBe("GRANDE");
    expect(petKindOf({ pet_type: "DOG", size: "", coat: "" })).toBeNull();
    expect(petKindOf({ pet_type: "CAT", size: "", coat: "LARGO" })).toBe("GATO_LARGO");
  });
});

describe("appointmentAddress", () => {
  it("toma la del cliente o la de las notas", () => {
    const c = client("a", "Sector Granada");
    expect(appointmentAddress(appt("1", "a", "15:30", "17:00"), [c])).toBe("Sector Granada");
    const loose = { ...appt("2", "x", "15:30", "17:00"), notes: "Dirección: Cumbres 12 · Es nervioso" };
    expect(appointmentAddress(loose, [])).toBe("Cumbres 12");
  });
});

describe("offersForDate", () => {
  it("con el día libre ofrece todos los horarios de la tabla", () => {
    const offers = offersForDate(MONDAY, 60, zone("granada"), [], [], config, now);
    expect(offers.map((o) => o.time)).toEqual(["15:30", "17:00", "18:30"]);
    expect(offers[0].end).toBe("16:30");
  });

  it("un perro grande de 3:30 a 5:30 bloquea las 5:00; las 6:30 sí cabe si el traslado alcanza", () => {
    const clients = [client("a", "Cumbres")];
    const busy = [appt("1", "a", "15:30", "17:30")];
    const offers = offersForDate(MONDAY, 60, zone("cerca"), busy, clients, config, now);
    expect(offers.map((o) => o.time)).toEqual(["18:30"]);
  });

  it("no ofrece las 5:00 si el servicio anterior termina 4:45 y el traslado no alcanza", () => {
    const clients = [client("a", "Sector Granada")];
    const busy = [appt("1", "a", "15:00", "16:45")];
    // Cumbres queda a 25 min + 10 de colchón: lo más pronto es 5:20.
    const far = offersForDate(MONDAY, 60, zone("cerca"), busy, clients, config, now);
    expect(far.map((o) => o.time)).toEqual(["18:30"]);
    // En la misma zona son 10 + 10 min: a las 5:05 ya se llega… pero tampoco a las 5:00.
    const near = offersForDate(MONDAY, 60, zone("granada"), busy, clients, config, now);
    expect(near.map((o) => o.time)).toEqual(["18:30"]);
  });

  it("revisa también que alcance a llegar a la cita siguiente", () => {
    const clients = [client("b", "San Jerónimo")];
    const busy = [appt("1", "b", "17:00", "18:00")];
    // Un perro de 1 h a las 3:30 termina 4:30; a San Jerónimo son 35 + 10 → 5:15 > 5:00.
    // Y a las 6:30 tampoco: la cita de San Jerónimo termina 6:00 y regresar toma 45 min.
    const offers = offersForDate(MONDAY, 60, zone("granada"), busy, clients, config, now);
    expect(offers).toEqual([]);
  });

  it("respeta la hora máxima para terminar", () => {
    const offers = offersForDate(MONDAY, 180, zone("granada"), [], [], config, now);
    expect(offers.map((o) => o.time)).toEqual(["15:30", "17:00", "18:30"]);
    const strict = withDefaults({ rules: { latestFinish: "20:00" } });
    expect(
      offersForDate(MONDAY, 180, zone("granada"), [], [], strict, now).map((o) => o.time),
    ).toEqual(["15:30", "17:00"]);
  });

  it("nunca ofrece hoy, días pasados, domingos ni zonas fuera de cobertura", () => {
    expect(offersForDate("2026-11-14", 60, zone("granada"), [], [], config, now)).toEqual([]);
    expect(offersForDate("2026-11-15", 60, zone("granada"), [], [], config, now)).toEqual([]);
    expect(offersForDate(MONDAY, 60, zone("fuera"), [], [], config, now)).toEqual([]);
  });
});

describe("suggestOffers", () => {
  it("pone primero los días con citas en la misma zona", () => {
    const clients = [client("a", "Cumbres")];
    // El miércoles ya hay alguien en Cumbres.
    const busy = [appt("1", "a", "15:30", "16:30", "2026-11-18")];
    const offers = suggestOffers(60, zone("cerca"), busy, clients, config, { now, limit: 2 });
    expect(offers.every((o) => o.date === "2026-11-18")).toBe(true);
    expect(offers.every((o) => o.sameZone)).toBe(true);
  });
});
