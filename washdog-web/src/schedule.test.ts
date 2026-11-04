import { describe, expect, it } from "vitest";
import {
  appointmentForSlot,
  mondayOf,
  slotLabel,
  slotsForDate,
  weekSchedule,
  weeklySlots,
} from "./schedule";
import type { Appointment, Client } from "./data/types";

const appointment = (over: Partial<Appointment>): Appointment => ({
  id: "a1",
  user_id: "u1",
  client_id: "c1",
  client_name: "Ana",
  pet_name: "Lola",
  service: "Baño",
  time: "15:30",
  end_time: "17:00",
  date: "2026-11-02",
  status: "SCHEDULED",
  notes: "",
  repeat_every_days: null,
  series_id: null,
  updated_at: "",
  ...over,
});

describe("horario semanal", () => {
  it("reproduce los horarios del calendario de Washdog", () => {
    const times = (day: number) => weeklySlots[day].map((s) => s.time);
    expect(times(1)).toEqual(["15:30", "17:00", "18:30"]);
    expect(times(2)).toEqual(["15:30", "17:00", "18:30"]);
    expect(times(3)).toEqual(["15:30", "17:00", "19:00"]);
    expect(times(4)).toEqual(["15:30", "17:00"]);
    expect(times(5)).toEqual(["15:30", "17:00", "18:30"]);
    expect(times(6)).toEqual(["10:00", "12:00", "14:00", "16:00"]);
    expect(times(0)).toEqual([]);
  });
  it("cada horario dura lo que separa a los de su día, sin empalmarse", () => {
    for (const slots of Object.values(weeklySlots))
      slots.forEach((s, i) => {
        expect(s.end > s.time).toBe(true);
        if (slots[i + 1]) expect(s.end <= slots[i + 1].time).toBe(true);
      });
    expect(weeklySlots[1][0]).toEqual({ time: "15:30", end: "17:00" });
    expect(weeklySlots[6][0]).toEqual({ time: "10:00", end: "12:00" });
  });
  it("da los horarios de una fecha según su día de la semana", () => {
    expect(slotsForDate("2026-11-03").map((s) => s.time)).toEqual([
      "15:30",
      "17:00",
      "18:30",
    ]); // martes
    expect(slotsForDate("2026-11-08")).toEqual([]); // domingo
    expect(slotsForDate("2026-11-07")).toHaveLength(4); // sábado
  });
});

describe("mondayOf", () => {
  it("regresa el lunes de cualquier día de esa semana, incluido el domingo", () => {
    expect(mondayOf("2026-11-02")).toBe("2026-11-02");
    expect(mondayOf("2026-11-04")).toBe("2026-11-02");
    expect(mondayOf("2026-11-08")).toBe("2026-11-02");
    expect(mondayOf("2026-11-09")).toBe("2026-11-09");
    expect(mondayOf("2026-12-01")).toBe("2026-11-30");
  });
});

describe("slotLabel", () => {
  it("usa formato de 12 horas con am y pm", () => {
    expect(slotLabel("15:30")).toBe("3:30pm");
    expect(slotLabel("10:00")).toBe("10:00am");
    expect(slotLabel("12:00")).toBe("12:00pm");
    expect(slotLabel("19:00")).toBe("7:00pm");
    expect(slotLabel("00:30")).toBe("12:30am");
  });
});

describe("weekSchedule", () => {
  const early = new Date("2026-11-01T08:00:00");
  it("arma lunes a domingo con sus horarios, todos libres si no hay citas", () => {
    const week = weekSchedule("2026-11-02", [], early);
    expect(week.map((d) => d.date)).toEqual([
      "2026-11-02",
      "2026-11-03",
      "2026-11-04",
      "2026-11-05",
      "2026-11-06",
      "2026-11-07",
      "2026-11-08",
    ]);
    expect(week.map((d) => d.cells.length)).toEqual([3, 3, 3, 2, 3, 4, 0]);
    expect(week.flatMap((d) => d.cells).every((c) => !c.appointment)).toBe(
      true,
    );
  });
  it("marca ocupado el horario de una cita y deja libres los demás", () => {
    const a = appointment({});
    const monday = weekSchedule("2026-11-02", [a], early)[0];
    expect(monday.cells[0].appointment).toBe(a);
    expect(monday.cells[1].appointment).toBeUndefined();
    expect(monday.extras).toEqual([]);
  });
  it("una cita de una hora ocupa el horario en el que empieza", () => {
    const a = appointment({ time: "17:00", end_time: "18:00" });
    const monday = weekSchedule("2026-11-02", [a], early)[0];
    expect(monday.cells.map((c) => !!c.appointment)).toEqual([
      false,
      true,
      false,
    ]);
  });
  it("una cita sin hora de salida se toma de una hora", () => {
    const a = appointment({ time: "18:30", end_time: "" });
    const monday = weekSchedule("2026-11-02", [a], early)[0];
    expect(monday.cells[2].appointment).toBe(a);
  });
  it("las citas fuera de los horarios van aparte y no ocupan ninguno", () => {
    const a = appointment({ time: "09:00", end_time: "10:00" });
    const monday = weekSchedule("2026-11-02", [a], early)[0];
    expect(monday.cells.every((c) => !c.appointment)).toBe(true);
    expect(monday.extras).toEqual([a]);
  });
  it("las citas de un domingo (sin horarios) quedan como extras", () => {
    const a = appointment({ date: "2026-11-08", time: "11:00", end_time: "12:00" });
    const sunday = weekSchedule("2026-11-02", [a], early)[6];
    expect(sunday.cells).toEqual([]);
    expect(sunday.extras).toEqual([a]);
  });
  it("ignora citas de otras semanas", () => {
    const a = appointment({ date: "2026-11-09" });
    const week = weekSchedule("2026-11-02", [a], early);
    expect(week.flatMap((d) => d.cells).some((c) => c.appointment)).toBe(false);
    expect(week.flatMap((d) => d.extras)).toEqual([]);
  });
  it("marca como pasados los horarios que ya empezaron", () => {
    const now = new Date("2026-11-02T17:30:00"); // lunes, ya pasaron 3:30pm y 5:00pm
    const monday = weekSchedule("2026-11-02", [], now)[0];
    expect(monday.cells.map((c) => c.past)).toEqual([true, true, false]);
    const tuesday = weekSchedule("2026-11-02", [], now)[1];
    expect(tuesday.cells.every((c) => !c.past)).toBe(true);
  });
});

describe("appointmentForSlot", () => {
  const client: Client = {
    id: "c1",
    user_id: "u1",
    owner_name: "Ana Torres",
    pet_name: "Lola",
    breed: "",
    last_visit: "",
    needs_reschedule: false,
    pet_type: "DOG",
    phone: "8112345678",
    address: "Av. Juárez 123",
    nervous: true,
    updated_at: "",
  };
  const picked = {
    date: "2026-11-03",
    slot: { time: "17:00", end: "18:30" },
  };
  it("crea la cita en la fecha y horario elegidos, ligada al cliente", () => {
    expect(
      appointmentForSlot(client, picked, "u1", "a1", new Date("2026-11-03T12:00:00Z")),
    ).toEqual({
      id: "a1",
      user_id: "u1",
      client_id: "c1",
      client_name: "Ana Torres",
      pet_name: "Lola",
      service: "Baño",
      time: "17:00",
      end_time: "18:30",
      date: "2026-11-03",
      status: "SCHEDULED",
      notes: "Dirección: Av. Juárez 123 · Es nervioso",
      repeat_every_days: null,
      series_id: null,
      updated_at: "2026-11-03T12:00:00.000Z",
    });
  });
  it("deja las notas vacías si no hay dirección ni nervioso", () => {
    expect(
      appointmentForSlot({ ...client, address: "", nervous: false }, picked, "u1", "a1")
        .notes,
    ).toBe("");
  });
});
