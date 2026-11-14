/**
 * "Cerebro" del bot de WhatsApp. Claude entiende el mensaje y redacta la
 * respuesta; los precios, tiempos, zonas y horarios los calcula `planner.ts`
 * con la configuración que Washdog edita en la app. El bot nunca confirma
 * citas: cuando hay que decidir algo, llama a la herramienta `avisar`.
 *
 * Se usa desde el servidor (api/whatsapp.ts). No depende de la red: el cliente
 * de Claude y la función de aviso se inyectan, así se puede probar.
 */
import type Anthropic from "@anthropic-ai/sdk";
import type { Appointment, BotAlert, Client } from "../data/types";
import { slotLabel, weeklySlots } from "../schedule.js";
import { petKindLabel, petKinds, sizeLabel, sizes, type BotConfig, type PetKind } from "./config.js";
import { needsReview, quote, suggestOffers, zoneFor, type Pet } from "./planner.js";

export const BOT_MODEL = "claude-haiku-5-5";
const MAX_TOOL_ROUNDS = 6;

export type ChatTurn = { role: "user" | "assistant"; text: string };
export type AlertInput = {
  kind: Exclude<BotAlert["kind"], "TEST">;
  summary: string;
  requested_date: string;
  requested_time: string;
  customer_name: string;
};
export type BotInput = {
  config: BotConfig;
  appointments: Appointment[];
  clients: Client[];
  /** Mensajes anteriores con este número, del más viejo al más nuevo. */
  history: ChatTurn[];
  text: string;
  phone: string;
  /** Nombre del perfil de WhatsApp. */
  profileName: string;
  /** Hora actual en Monterrey (sus campos locales = hora de Monterrey). */
  now: Date;
  createMessage: (params: Anthropic.MessageCreateParamsNonStreaming) => Promise<Anthropic.Message>;
  notify: (alert: AlertInput) => Promise<void>;
};
export type BotOutput = { reply: string | null; alerts: AlertInput[] };

const money = (n: number) => `$${n.toLocaleString("es-MX")}`;
const priceText = (price: number, from?: boolean) =>
  price === 0 ? "gratis" : `${from ? "desde " : ""}${money(price)}`;
const minutesText = (m: number) => {
  const h = Math.floor(m / 60);
  const r = m % 60;
  return [h && `${h} h`, r && `${r} min`].filter(Boolean).join(" ") || "0 min";
};
const dayNames = ["domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"];
const isoLocal = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const dateLabel = (iso: string) => {
  const d = new Date(`${iso}T12:00:00`);
  return `${dayNames[d.getDay()]} ${d.getDate()}/${d.getMonth() + 1}`;
};

/** Cliente ya registrado con ese número (compara los últimos 10 dígitos). */
export function knownClients(phone: string, clients: Client[]): Client[] {
  const tail = phone.replace(/\D/g, "").slice(-10);
  if (tail.length < 10) return [];
  return clients.filter((c) => c.phone.replace(/\D/g, "").slice(-10) === tail);
}

export function systemPrompt(config: BotConfig, now: Date): string {
  const t = config.texts;
  const baths = petKinds
    .map((k) => `- Baño ${petKindLabel[k].toLowerCase()}: ${priceText(config.baths[k].price, config.baths[k].from)}`)
    .join("\n");
  const sized = config.sizedExtras
    .map(
      (e) =>
        `- ${e.name}: ${sizes.map((s) => `${sizeLabel[s].toLowerCase()} ${priceText(e.bySize[s].price, e.bySize[s].from)}`).join(", ")}`,
    )
    .join("\n");
  const flat = config.extras.map((e) => `- ${e.name}: ${priceText(e.price, e.from)}`).join("\n");
  const zones = config.zones
    .map((z) =>
      z.mode === "ALERT"
        ? `- ${z.name}: NO ofrecer horarios; avisar (tipo OUT_OF_ZONE).`
        : `- ${z.name}: domicilio ${priceText(z.fee, z.feeFrom)}.`,
    )
    .join("\n");
  const schedule = [1, 2, 3, 4, 5, 6, 0]
    .map((d) => {
      const slots = weeklySlots[d] ?? [];
      return `- ${dayNames[d]}: ${slots.length ? slots.map((s) => slotLabel(s.time)).join(", ") : "cerrado"}`;
    })
    .join("\n");
  return `Eres el asistente de WhatsApp de Washdog ("${t.assistantName}"), un servicio de baño de perros y gatos a domicilio en Monterrey, N.L. La base está en Mitras Poniente, Sector Granada. Atiende una sola persona.

Hoy es ${dateLabel(isoLocal(now))} (${isoLocal(now)}), son las ${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")} en Monterrey.

TU TRABAJO: contestar dudas de precios, servicios, zonas y horarios disponibles, como un secretario amable. Escribe en español de México, corto y claro (es WhatsApp), con uno o dos emojis como mucho. Tutea al cliente.
FORMATO WHATSAPP: para negritas usa un solo asterisco (*así*), nunca dos. No uses títulos con # ni tablas. Listas cortas con guion.

REGLAS QUE NUNCA ROMPES:
1. Nunca confirmes, agendes ni apartes una cita. Tú no puedes. Cuando el cliente elige o pide un día y hora ("agéndame el jueves a las 5"): si ya sabes talla y colonia, primero revisa con horarios_disponibles (dia_preferido = ese día) que ese horario esté libre; si no está libre, ofrécele las alternativas y no avises. Si está libre (o no tienes cómo revisarlo), llama a la herramienta avisar con tipo BOOKING y contesta algo como: "${t.bookingRequested}"
2. Si piden cita para HOY (el mismo día), no ofrezcas horarios: llama avisar con tipo SAME_DAY y contesta algo como: "${t.sameDay}"
3. Si la dirección queda fuera de zona, llama avisar con tipo OUT_OF_ZONE y contesta algo como: "${t.outOfZone}"
4. Si el perro trae muchos nudos o son varios perros grandes, pide foto y llama avisar con tipo REVIEW. Texto sugerido: "${t.needsReview}"
5. Si no sabes algo o el cliente pide hablar con una persona, llama avisar con tipo REVIEW y contesta algo como: "${t.unknown}"
6. Los precios, la duración y los horarios libres SOLO salen de las herramientas cotizar y horarios_disponibles. No inventes precios, horarios ni descuentos. Para ofrecer horarios necesitas saber la talla (o si es gato y su pelo) y la colonia o dirección; si falta algo, pregúntalo. En cuanto tengas talla y colonia, llama horarios_disponibles de inmediato (asume baño básico sin nudos si no dijo otra cosa) y ofrece opciones concretas; no preguntes primero por extras.
7. Al ofrecer horarios, da como mucho 3 o 4 opciones y menciona el total aproximado.
8. Si el cliente pregunta por "horario" en general, dile los días y horas de atención y ofrece revisar disponibilidad.
9. Tus mensajes anteriores en esta conversación ya salieron de las herramientas: confía en ellos, no los pongas en duda frente al cliente.

LISTA DE PRECIOS (referencia; para cotizar usa la herramienta):
${baths}
${sized}
${flat}
${t.bathIncludes}

ZONAS Y DOMICILIO:
${zones}

HORARIOS DE ATENCIÓN (hora de llegada):
${schedule}

Saludo sugerido para el primer mensaje: "${t.greeting}"`;
}

const petSchema = (extraIds: string[]) => ({
  type: "object" as const,
  properties: {
    tipo: { type: "string", enum: [...petKinds], description: "Talla del perro, o gato con su tipo de pelo." },
    extras: {
      type: "array",
      items: { type: "string", enum: extraIds },
      description: "Servicios extra que pidió (ids).",
    },
    nudos: { type: "boolean", description: "Trae muchos nudos o el pelo muy enredado." },
  },
  required: ["tipo", "extras", "nudos"],
  additionalProperties: false,
});

export function botTools(config: BotConfig): Anthropic.Tool[] {
  const extraIds = [...config.sizedExtras, ...config.extras].map((e) => e.id);
  const extraHelp = [...config.sizedExtras, ...config.extras].map((e) => `${e.id} = ${e.name}`).join("; ");
  const pets = { type: "array", items: petSchema(extraIds), minItems: 1 };
  return [
    {
      name: "cotizar",
      description: `Calcula precio total y duración de un servicio para una o varias mascotas, incluyendo domicilio según la dirección. Extras: ${extraHelp}.`,
      strict: true,
      input_schema: {
        type: "object",
        properties: {
          mascotas: pets,
          direccion: { type: "string", description: "Colonia o dirección del cliente; vacío si no la sabes." },
        },
        required: ["mascotas", "direccion"],
        additionalProperties: false,
      },
    },
    {
      name: "horarios_disponibles",
      description:
        "Horarios libres de los próximos días en los que cabe el servicio, contando duración y traslados. Nunca incluye hoy. Requiere talla y dirección.",
      strict: true,
      input_schema: {
        type: "object",
        properties: {
          mascotas: pets,
          direccion: { type: "string", description: "Colonia o dirección del cliente." },
          dia_preferido: {
            type: "string",
            description: "Fecha YYYY-MM-DD si el cliente pidió un día específico; vacío si no.",
          },
        },
        required: ["mascotas", "direccion", "dia_preferido"],
        additionalProperties: false,
      },
    },
    {
      name: "avisar",
      description:
        "Deja un aviso a Washdog (notificación en su app) para que una persona decida. Úsalo cuando piden agendar, piden para hoy, están fuera de zona, hay nudos o varios perros grandes, o no sabes qué contestar.",
      strict: true,
      input_schema: {
        type: "object",
        properties: {
          tipo: { type: "string", enum: ["BOOKING", "SAME_DAY", "OUT_OF_ZONE", "REVIEW"] },
          resumen: {
            type: "string",
            description: "Qué quiere el cliente en una o dos frases: mascota(s), servicio, dirección, día y hora pedidos.",
          },
          fecha: { type: "string", description: "Fecha pedida YYYY-MM-DD, o vacío." },
          hora: { type: "string", description: "Hora pedida HH:MM en 24 h, o vacío." },
          nombre_cliente: { type: "string", description: "Nombre del cliente si lo dijo, o vacío." },
        },
        required: ["tipo", "resumen", "fecha", "hora", "nombre_cliente"],
        additionalProperties: false,
      },
    },
  ];
}

type PetArg = { tipo: string; extras: string[]; nudos: boolean };
const toPets = (list: PetArg[]): Pet[] =>
  list
    .filter((p) => (petKinds as readonly string[]).includes(p.tipo))
    .map((p) => ({ kind: p.tipo as PetKind, extras: p.extras ?? [], matted: Boolean(p.nudos) }));

/** Ejecuta una herramienta y devuelve el texto que verá Claude. */
export async function runTool(
  name: string,
  input: Record<string, unknown>,
  ctx: Pick<BotInput, "config" | "appointments" | "clients" | "now" | "notify" | "profileName">,
  alerts: AlertInput[],
): Promise<string> {
  const { config } = ctx;
  if (name === "avisar") {
    const kind = String(input.tipo) as AlertInput["kind"];
    const alert: AlertInput = {
      kind: ["BOOKING", "SAME_DAY", "OUT_OF_ZONE", "REVIEW"].includes(kind) ? kind : "REVIEW",
      summary: String(input.resumen ?? "").slice(0, 1500) || "El cliente necesita atención.",
      requested_date: /^\d{4}-\d{2}-\d{2}$/.test(String(input.fecha)) ? String(input.fecha) : "",
      requested_time: /^\d{2}:\d{2}$/.test(String(input.hora)) ? String(input.hora) : "",
      customer_name: String(input.nombre_cliente || ctx.profileName || "").slice(0, 120),
    };
    await ctx.notify(alert);
    alerts.push(alert);
    return "Aviso enviado. Dile al cliente que en un momento le confirman; no confirmes nada tú.";
  }
  const pets = toPets((input.mascotas as PetArg[]) ?? []);
  if (!pets.length) return "Falta saber la talla del perro (pequeño, mediano, grande o jumbo) o si es gato y su pelo.";
  const address = String(input.direccion ?? "");
  const zone = zoneFor(address, config);
  const q = quote(pets, zone, config);
  const lines = q.lines.map((l) => `${l.label}: ${priceText(l.price, l.from)}`).join("; ");
  const review = needsReview(pets, config);
  if (name === "cotizar") {
    return [
      `Zona: ${zone.name}${zone.mode === "ALERT" ? " (FUERA DE ZONA: no ofrecer horarios, avisar OUT_OF_ZONE)" : ""}.`,
      `Desglose: ${lines}.`,
      `Total: ${priceText(q.price, q.from)}. Duración aproximada: ${minutesText(q.minutes)}.`,
      review ? "Este caso requiere revisión: pide foto y avisa con tipo REVIEW." : "",
      address ? "" : "No hay dirección: el domicilio puede cambiar según la colonia.",
    ]
      .filter(Boolean)
      .join(" ");
  }
  if (name === "horarios_disponibles") {
    if (!address.trim()) return "Falta la colonia o dirección para calcular traslados. Pregúntala.";
    if (zone.mode === "ALERT") return `${zone.name} está fuera de zona: no ofrezcas horarios, avisa con tipo OUT_OF_ZONE.`;
    if (review) return "Nudos o varios perros grandes: no ofrezcas horarios; pide foto y avisa con tipo REVIEW.";
    const today = isoLocal(ctx.now);
    const wanted = String(input.dia_preferido ?? "");
    if (wanted === today) return "Pidió para hoy: no ofrezcas horarios, avisa con tipo SAME_DAY.";
    const offers = suggestOffers(q.minutes, zone, ctx.appointments, ctx.clients, config, {
      now: ctx.now,
      days: 14,
      limit: 30,
    });
    const picked = (/^\d{4}-\d{2}-\d{2}$/.test(wanted) ? offers.filter((o) => o.date === wanted) : offers).slice(0, 4);
    if (!picked.length)
      return wanted
        ? `No hay horario libre el ${dateLabel(wanted)} para este servicio. ${offers.length ? `Otras opciones: ${offers.slice(0, 3).map((o) => `${dateLabel(o.date)} ${slotLabel(o.time)}`).join(", ")}.` : "Avisa con tipo REVIEW."}`
        : "No hay horarios libres en los próximos 14 días. Avisa con tipo REVIEW.";
    return `Duración ${minutesText(q.minutes)}, total ${priceText(q.price, q.from)}. Horarios libres: ${picked
      .map((o) => `${dateLabel(o.date)} a las ${slotLabel(o.time)}`)
      .join("; ")}. Recuerda: solo ofrécelos, no los apartes.`;
  }
  return "Herramienta desconocida.";
}

/** Procesa un mensaje del cliente y devuelve la respuesta para WhatsApp. */
export async function handleMessage(input: BotInput): Promise<BotOutput> {
  const alerts: AlertInput[] = [];
  if (!input.config.enabled) {
    await input.notify({
      kind: "REVIEW",
      summary: `Bot apagado. Mensaje: ${input.text}`.slice(0, 1500),
      requested_date: "",
      requested_time: "",
      customer_name: input.profileName,
    });
    return { reply: null, alerts };
  }
  const known = knownClients(input.phone, input.clients);
  const context = known.length
    ? `\n\nEste número ya es cliente: ${known
        .map((c) => `${c.owner_name}, mascota ${c.pet_name}${c.breed ? ` (${c.breed})` : ""}${c.size ? `, talla ${c.size.toLowerCase()}` : ""}${c.address ? `, vive en ${c.address}` : ""}`)
        .join("; ")}.`
    : "";
  const messages: Anthropic.MessageParam[] = [];
  for (const turn of input.history.slice(-12)) {
    const last = messages[messages.length - 1];
    if (last && last.role === turn.role) last.content = `${last.content as string}\n${turn.text}`;
    else messages.push({ role: turn.role, content: turn.text });
  }
  if (messages[0]?.role === "assistant") messages.shift();
  const lastTurn = messages[messages.length - 1];
  if (lastTurn?.role === "user") lastTurn.content = `${lastTurn.content as string}\n${input.text}`;
  else messages.push({ role: "user", content: input.text });

  const system = systemPrompt(input.config, input.now) + context;
  const tools = botTools(input.config);
  for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
    const response = await input.createMessage({
      model: BOT_MODEL,
      max_tokens: 4000,
      system,
      tools,
      messages,
    });
    if (response.stop_reason === "refusal") break;
    const toolUses = response.content.filter((b): b is Anthropic.ToolUseBlock => b.type === "tool_use");
    if (response.stop_reason !== "tool_use" || !toolUses.length) {
      const text = response.content
        .filter((b): b is Anthropic.TextBlock => b.type === "text")
        .map((b) => b.text)
        .join("\n")
        .trim();
      return { reply: text ? text.slice(0, 4000) : null, alerts };
    }
    messages.push({ role: "assistant", content: response.content });
    const results: Anthropic.ToolResultBlockParam[] = [];
    for (const use of toolUses) {
      let content: string;
      try {
        content = await runTool(use.name, (use.input ?? {}) as Record<string, unknown>, input, alerts);
      } catch (error) {
        content = `Error: ${error instanceof Error ? error.message : "falló la herramienta"}. Avisa con tipo REVIEW.`;
      }
      results.push({ type: "tool_result", tool_use_id: use.id, content });
    }
    messages.push({ role: "user", content: results });
  }
  // Sin respuesta útil: que lo vea una persona.
  if (!alerts.length) {
    const alert: AlertInput = {
      kind: "REVIEW",
      summary: `El bot no supo contestar: ${input.text}`.slice(0, 1500),
      requested_date: "",
      requested_time: "",
      customer_name: input.profileName,
    };
    await input.notify(alert);
    alerts.push(alert);
  }
  return { reply: input.config.texts.unknown, alerts };
}
