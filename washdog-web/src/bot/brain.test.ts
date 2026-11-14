import { describe, expect, it, vi } from "vitest";
import type Anthropic from "@anthropic-ai/sdk";
import { defaultBotConfig, withDefaults } from "./config";
import { botTools, handleMessage, knownClients, runTool, systemPrompt, type BotInput } from "./brain";
import type { Client } from "../data/types";

// Sábado 14 de noviembre de 2026, 10:00 en Monterrey.
const now = new Date("2026-11-14T10:00:00");
const config = defaultBotConfig();

const msg = (content: Anthropic.ContentBlock[], stop: Anthropic.Message["stop_reason"]): Anthropic.Message =>
  ({ id: "m", type: "message", role: "assistant", model: "x", content, stop_reason: stop, stop_sequence: null, usage: {} }) as unknown as Anthropic.Message;
const text = (t: string) => ({ type: "text", text: t, citations: null }) as Anthropic.TextBlock;
const use = (name: string, input: unknown, id = "t1") =>
  ({ type: "tool_use", id, name, input }) as Anthropic.ToolUseBlock;

function input(over: Partial<BotInput> = {}): BotInput {
  return {
    config,
    appointments: [],
    clients: [],
    history: [],
    text: "Hola",
    phone: "5218112345678",
    profileName: "Ana",
    now,
    createMessage: vi.fn(),
    notify: vi.fn(async () => {}),
    ...over,
  };
}

describe("systemPrompt", () => {
  it("incluye precios, zonas, horarios y la fecha de hoy", () => {
    const prompt = systemPrompt(config, now);
    expect(prompt).toContain("Baño perro grande: $320");
    expect(prompt).toContain("Sector Granada: domicilio gratis");
    expect(prompt).toContain("NO ofrecer horarios");
    expect(prompt).toContain("2026-11-14");
    expect(prompt).toContain("sábado: 10:00am, 12:00pm, 2:00pm, 4:00pm");
    expect(prompt).toContain("Nunca confirmes");
  });
});

describe("botTools", () => {
  it("define herramientas estrictas con los extras configurados", () => {
    const tools = botTools(config);
    expect(tools.map((t) => t.name)).toEqual(["cotizar", "horarios_disponibles", "avisar"]);
    expect(tools.every((t) => t.strict)).toBe(true);
    expect(JSON.stringify(tools[0].input_schema)).toContain('"deslanado"');
  });
});

describe("runTool", () => {
  const ctx = { config, appointments: [], clients: [], now, notify: vi.fn(async () => {}), profileName: "Ana" };
  it("cotiza con zona y duración", async () => {
    const out = await runTool(
      "cotizar",
      { mascotas: [{ tipo: "GRANDE", extras: ["cepillado"], nudos: false }], direccion: "Cumbres" },
      ctx,
      [],
    );
    expect(out).toContain("Total: desde $550");
    expect(out).toContain("3 h");
  });
  it("ofrece horarios futuros y nunca hoy", async () => {
    const out = await runTool(
      "horarios_disponibles",
      { mascotas: [{ tipo: "PEQUENO", extras: [], nudos: false }], direccion: "Sector Granada", dia_preferido: "" },
      ctx,
      [],
    );
    expect(out).toContain("lunes 16/11 a las 3:30pm");
    expect(out).not.toContain("14/11");
    const today = await runTool(
      "horarios_disponibles",
      { mascotas: [{ tipo: "PEQUENO", extras: [], nudos: false }], direccion: "Sector Granada", dia_preferido: "2026-11-14" },
      ctx,
      [],
    );
    expect(today).toContain("SAME_DAY");
  });
  it("no ofrece horarios fuera de zona ni con nudos", async () => {
    const far = await runTool(
      "horarios_disponibles",
      { mascotas: [{ tipo: "PEQUENO", extras: [], nudos: false }], direccion: "Apodaca", dia_preferido: "" },
      ctx,
      [],
    );
    expect(far).toContain("OUT_OF_ZONE");
    const matted = await runTool(
      "horarios_disponibles",
      { mascotas: [{ tipo: "MEDIANO", extras: [], nudos: true }], direccion: "Cumbres", dia_preferido: "" },
      ctx,
      [],
    );
    expect(matted).toContain("REVIEW");
  });
  it("avisar manda la notificación con los datos limpios", async () => {
    const notify = vi.fn(async () => {});
    const alerts: unknown[] = [];
    await runTool(
      "avisar",
      { tipo: "BOOKING", resumen: "Jueves 5pm, shih tzu en Cumbres", fecha: "2026-11-19", hora: "17:00", nombre_cliente: "" },
      { ...ctx, notify },
      alerts as never,
    );
    expect(notify).toHaveBeenCalledWith({
      kind: "BOOKING",
      summary: "Jueves 5pm, shih tzu en Cumbres",
      requested_date: "2026-11-19",
      requested_time: "17:00",
      customer_name: "Ana",
    });
  });
});

describe("handleMessage", () => {
  it("contesta directo cuando Claude no usa herramientas", async () => {
    const createMessage = vi.fn(async () => msg([text("¡Hola! 🐶 ¿En qué te ayudo?")], "end_turn"));
    const out = await handleMessage(input({ createMessage }));
    expect(out.reply).toBe("¡Hola! 🐶 ¿En qué te ayudo?");
    expect(createMessage).toHaveBeenCalledTimes(1);
  });

  it("ejecuta herramientas y devuelve la respuesta final", async () => {
    const createMessage = vi
      .fn()
      .mockResolvedValueOnce(
        msg(
          [use("avisar", { tipo: "BOOKING", resumen: "Quiere jueves 5pm", fecha: "2026-11-19", hora: "17:00", nombre_cliente: "Ana" })],
          "tool_use",
        ),
      )
      .mockResolvedValueOnce(msg([text("¡Perfecto! Ya pasé tu solicitud 🐾")], "end_turn"));
    const notify = vi.fn(async () => {});
    const out = await handleMessage(input({ createMessage, notify, text: "Agéndame el jueves a las 5" }));
    expect(out.reply).toBe("¡Perfecto! Ya pasé tu solicitud 🐾");
    expect(out.alerts).toHaveLength(1);
    expect(notify).toHaveBeenCalledOnce();
    const second = createMessage.mock.calls[1][0] as Anthropic.MessageCreateParamsNonStreaming;
    const last = second.messages[second.messages.length - 1];
    expect(JSON.stringify(last.content)).toContain("tool_result");
  });

  it("une el historial y nunca empieza con el asistente", async () => {
    const createMessage = vi.fn(async () => msg([text("ok")], "end_turn"));
    await handleMessage(
      input({
        createMessage,
        history: [
          { role: "assistant", text: "Hola" },
          { role: "user", text: "Precio" },
          { role: "user", text: "de baño" },
        ],
        text: "para un pug",
      }),
    );
    const params = (createMessage.mock.calls[0] as unknown as [Anthropic.MessageCreateParamsNonStreaming])[0];
    expect(params.messages).toEqual([{ role: "user", content: "Precio\nde baño\npara un pug" }]);
    expect(params.model).toBe("claude-haiku-5-5");
  });

  it("con el bot apagado no contesta y deja aviso", async () => {
    const notify = vi.fn(async () => {});
    const createMessage = vi.fn();
    const out = await handleMessage(input({ config: withDefaults({ enabled: false }), notify, createMessage }));
    expect(out.reply).toBeNull();
    expect(notify).toHaveBeenCalledOnce();
    expect(createMessage).not.toHaveBeenCalled();
  });

  it("si Claude se rehúsa, avisa a una persona", async () => {
    const notify = vi.fn(async () => {});
    const out = await handleMessage(
      input({ notify, createMessage: vi.fn(async () => msg([], "refusal")) }),
    );
    expect(out.reply).toBe(config.texts.unknown);
    expect(notify).toHaveBeenCalledOnce();
  });
});

describe("knownClients", () => {
  it("reconoce al cliente por los últimos 10 dígitos", () => {
    const c = { phone: "81 1234 5678" } as Client;
    expect(knownClients("5218112345678", [c])).toEqual([c]);
    expect(knownClients("5218100000000", [c])).toEqual([]);
  });
});
