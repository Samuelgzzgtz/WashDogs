import { describe, expect, it } from "vitest";
import {
  addDays,
  appointmentClient,
  clientsToRemind,
  coinQuantities,
  coinTotals,
  findConflict,
  goalPercent,
  goalRemaining,
  missingAppointments,
  parseCoinQuantity,
  recurringAppointments,
  totals,
  transactionsCsv,
  validateContribution,
  validateGoal,
  visibleRows,
  whatsapp,
} from "./domain";
import type { Appointment, Client, Tombstone, Transaction } from "./data/types";

const client: Client = {
  id: "c1",
  user_id: "u1",
  owner_name: "Samuel",
  pet_name: "Luna",
  breed: "",
  last_visit: "2026-09-01",
  needs_reschedule: false,
  pet_type: "DOG",
  phone: "8112345678",
  updated_at: "",
};
const appointment: Appointment = {
  id: "a1",
  user_id: "u1",
  client_id: "c1",
  client_name: "Samuel",
  pet_name: "Luna",
  service: "Baño",
  time: "09:00",
  end_time: "10:00",
  date: "2026-10-23",
  status: "SCHEDULED",
  notes: "",
  repeat_every_days: null,
  series_id: null,
  updated_at: "",
};
const tx: Transaction = {
  id: "t1",
  user_id: "u1",
  amount: 250,
  type: "IN",
  concept: "Baño",
  detail: "",
  date: "2026-10-23T10:00",
  updated_at: "",
};

describe("agenda y compatibilidad Android", () => {
  it("conserva días locales al cruzar mes y año", () => {
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2028-02-28", 1)).toBe("2028-02-29");
  });
  it("detecta traslapes sin bloquear citas contiguas y conserva la duración legacy de una hora", () => {
    expect(
      findConflict({ ...appointment, id: "a2", time: "09:30" }, [appointment]),
    ).toBe(appointment);
    expect(
      findConflict(
        { ...appointment, id: "a2", time: "10:00", end_time: "11:00" },
        [appointment],
      ),
    ).toBeUndefined();
    expect(
      findConflict({ ...appointment, id: "a2", time: "09:30" }, [
        { ...appointment, end_time: "" },
      ]),
    ).toBeDefined();
    expect(findConflict(appointment, [appointment])).toBeUndefined();
  });
  it("genera IDs de recurrencia idénticos a Android sin duplicar la cita original", () => {
    const base = { ...appointment, repeat_every_days: 7, series_id: "series" };
    expect(
      recurringAppointments(base, "2026-11-06").map((a) => [a.id, a.date]),
    ).toEqual([
      ["a1", "2026-10-23"],
      ["series@2026-10-30", "2026-10-30"],
      ["series@2026-11-06", "2026-11-06"],
    ]);
  });
  it("respeta tombstones con timestamps de distinta precisión y permite ediciones posteriores", () => {
    const tombstone: Tombstone = {
      user_id: "u1",
      entity: "clients",
      entity_id: "c1",
      deleted_at: "2026-10-23T10:00:00Z",
    };
    expect(
      visibleRows(
        [{ ...client, updated_at: "2026-10-23T10:00:00.000Z" }],
        [tombstone],
        "clients",
      ),
    ).toEqual([]);
    expect(
      visibleRows(
        [{ ...client, updated_at: "2026-10-23T10:00:01Z" }],
        [tombstone],
        "clients",
      ),
    ).toHaveLength(1);
    expect(visibleRows([client], [tombstone], "appointments")).toHaveLength(1);
  });
  it("extiende series activas sin resucitar borrados ni duplicar citas o crear traslapes", () => {
    const base = { ...appointment, repeat_every_days: 7, series_id: "series" };
    const deleted: Tombstone = {
      user_id: "u1",
      entity: "appointments",
      entity_id: "series@2026-11-06",
      deleted_at: "2026-10-23T12:00:00Z",
    };
    const rows = [base, { ...appointment, id: "busy", date: "2026-11-13" }];
    const series = [
      { id: "series", user_id: "u1", template: base, stopped_at: "" },
    ];
    const missing = missingAppointments(rows, [deleted], series, "2026-10-30");
    expect(missing[0].id).toBe("series@2026-10-30");
    expect(
      missing.some(
        (a) =>
          a.id === deleted.entity_id ||
          a.date === "2026-11-13" ||
          a.id === base.id,
      ),
    ).toBe(false);
    expect(
      missingAppointments(
        [...rows, ...missing],
        [deleted],
        series,
        "2026-10-30",
      ),
    ).toEqual([]);
    expect(
      missingAppointments(
        [{ ...base, repeat_every_days: null }],
        [],
        [{ ...series[0], stopped_at: "2026-10-30T10:00:00Z" }],
        "2026-10-30",
      ),
    ).toEqual([]);
    expect(
      missingAppointments(
        rows,
        [{ ...deleted, entity: "appointment_series", entity_id: "series" }],
        series,
        "2026-10-30",
      ),
    ).toEqual([]);
  });
  it("mover una sola ocurrencia no cambia las fechas ni los horarios futuros de la serie", () => {
    const base = { ...appointment, repeat_every_days: 7, series_id: "series" };
    const series = [
      { id: "series", user_id: "u1", template: base, stopped_at: "" },
    ];
    const edited = {
      ...base,
      date: "2026-10-24",
      time: "11:00",
      end_time: "12:00",
    };
    const missing = missingAppointments([edited], [], series, "2026-10-24");
    expect(missing[0].date).toBe("2026-10-30");
    expect(missing.every((a) => a.time === "09:00")).toBe(true);
    expect(missing.some((a) => a.date === "2026-10-31")).toBe(false);
  });
  it("resuelve nombres actuales por ID y no confunde mascotas con el mismo dueño en datos legacy", () => {
    expect(
      appointmentClient(appointment, [{ ...client, owner_name: "Otro nombre" }])
        ?.owner_name,
    ).toBe("Otro nombre");
    expect(
      appointmentClient({ ...appointment, client_id: "", pet_name: "Toby" }, [
        client,
      ]),
    ).toBeUndefined();
  });
  it("solo recuerda clientes atrasados sin citas pendientes", () => {
    expect(clientsToRemind([client], [appointment], "2026-10-23")).toEqual([]);
    expect(
      clientsToRemind(
        [client],
        [{ ...appointment, status: "DONE" }],
        "2026-10-23",
      ),
    ).toEqual([client]);
    expect(
      clientsToRemind([{ ...client, last_visit: "" }], [], "2026-10-23"),
    ).toEqual([]);
  });
});

describe("dinero y WhatsApp", () => {
  it("limita el balance al mes y conserva ingresos y gastos por separado", () => {
    expect(
      totals(
        [
          tx,
          { ...tx, id: "t2", type: "OUT", amount: 50 },
          { ...tx, id: "t3", date: "2026-09-01", amount: 999 },
        ],
        "2026-10",
      ),
    ).toEqual({ income: 250, expense: 50 });
  });
  it("exporta CSV sin ejecutar fórmulas ni romper campos con comillas o saltos de línea", () => {
    const csv = transactionsCsv([
      { ...tx, concept: "=1+1", detail: 'Luna, "baño"\nlisto' },
    ]);
    expect(csv).toContain('"\'=1+1"');
    expect(csv).toContain('"Luna, ""baño""\nlisto"');
    expect(csv.startsWith("\ufeff")).toBe(true);
  });
  it("normaliza México y teléfonos internacionales e interpola los mensajes", () => {
    const link = whatsapp(
      "(81) 1234-5678",
      "Hola {{nombre}}: {{fecha}} {{hora}}",
      "María & José",
      "23/10",
      "09:00",
    );
    expect(link).toBe(
      `https://wa.me/528112345678?text=${encodeURIComponent("Hola María & José: 23/10 09:00")}`,
    );
    expect(whatsapp("+1 415 555 1234", "Hola", "")).toContain(
      "https://wa.me/14155551234",
    );
    expect(whatsapp("", "", "")).toBeNull();
  });
});

describe("metas", () => {
  it("calcula porcentaje acotado entre 0 y 100", () => {
    expect(goalPercent(0, 1000)).toBe(0);
    expect(goalPercent(250, 1000)).toBe(25);
    expect(goalPercent(999, 1000)).toBe(99);
    expect(goalPercent(1500, 1000)).toBe(100);
    expect(goalPercent(100, 0)).toBe(0);
    expect(goalPercent(-5, 100)).toBe(0);
  });
  it("calcula lo que falta sin bajar de cero ni arrastrar decimales", () => {
    expect(goalRemaining(250, 1000)).toBe(750);
    expect(goalRemaining(0.1 + 0.2, 1)).toBe(0.7);
    expect(goalRemaining(1200, 1000)).toBe(0);
  });
  it("valida metas y abonos", () => {
    expect(validateGoal({ name: " ", target: 10, current: 0 })).toMatch(
      /nombre/,
    );
    expect(validateGoal({ name: "Equipo", target: 0, current: 0 })).toMatch(
      /mayor a cero/,
    );
    expect(validateGoal({ name: "Equipo", target: 10, current: -1 })).toMatch(
      /negativo/,
    );
    expect(validateGoal({ name: "Equipo", target: NaN, current: 0 })).not.toBe(
      null,
    );
    expect(validateGoal({ name: "Equipo", target: 10, current: 0 })).toBeNull();
    expect(validateContribution(0)).not.toBeNull();
    expect(validateContribution(NaN)).not.toBeNull();
    expect(validateContribution(50)).toBeNull();
  });
});

describe("conteo de monedas", () => {
  it("suma monedas y pesos sin errores de punto flotante", () => {
    expect(coinTotals(coinQuantities([]))).toEqual({ count: 0, sum: 0 });
    expect(coinTotals({ 0.5: 3, 1: 2, 2: 1, 5: 4, 10: 5, 20: 1 })).toEqual({
      count: 16,
      sum: 95.5,
    });
    expect(coinTotals({ 0.5: 1, 1: 0, 2: 0, 5: 0, 10: 0, 20: 0 }).sum).toBe(
      0.5,
    );
    expect(coinTotals({ 0.5: 3 }).sum).toBe(1.5);
  });
  it("una denominación sin fila cuenta 0 e ignora denominaciones inválidas", () => {
    const row = (denomination: number, quantity: number) => ({
      id: String(denomination),
      user_id: "u1",
      denomination,
      quantity,
      updated_at: "",
    });
    const q = coinQuantities([row(5, 7), row(3, 9)]);
    expect(q[5]).toBe(7);
    expect(q[10]).toBe(0);
    expect(3 in q).toBe(false);
  });
  it("valida cantidades enteras no negativas", () => {
    expect(parseCoinQuantity("")).toBe(0);
    expect(parseCoinQuantity("12")).toBe(12);
    expect(parseCoinQuantity(0)).toBe(0);
    expect(parseCoinQuantity("-1")).toBeNull();
    expect(parseCoinQuantity("1.5")).toBeNull();
    expect(parseCoinQuantity("abc")).toBeNull();
    expect(parseCoinQuantity(2_000_000)).toBeNull();
  });
});
