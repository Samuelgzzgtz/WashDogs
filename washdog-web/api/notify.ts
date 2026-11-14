import { asService, configureWebPush, json, pushToUser } from "./_push.js";

const kinds = ["BOOKING", "SAME_DAY", "OUT_OF_ZONE", "REVIEW"] as const;
type Kind = (typeof kinds)[number];
const titles: Record<Kind, string> = {
  BOOKING: "📅 Quieren agendar",
  SAME_DAY: "⚡ Piden cita para hoy",
  OUT_OF_ZONE: "📍 Cliente fuera de zona",
  REVIEW: "🔎 Revisar antes de agendar",
};
const text = (value: unknown, max: number) => String(value ?? "").slice(0, max);

/**
 * El bot de WhatsApp llama aquí cuando una persona tiene que decidir: guarda el
 * aviso en la app y manda la notificación. Protegido con BOT_SECRET.
 */
export async function POST(request: Request) {
  const secret = process.env.BOT_SECRET?.trim();
  if (!secret || request.headers.get("x-bot-secret") !== secret)
    return json({ error: "No autorizado." }, 401);
  const missing = configureWebPush();
  if (missing) return json({ error: missing }, 500);
  const db = asService();
  if (!db) return json({ error: "Falta SUPABASE_SERVICE_ROLE_KEY en el servidor." }, 500);

  const body = (await request.json().catch(() => null)) as Record<string, unknown> | null;
  const kind = body?.kind as Kind;
  const userId = text(body?.user_id, 64);
  const summary = text(body?.summary, 2000).trim();
  if (!body || !kinds.includes(kind) || !userId || !summary)
    return json({ error: "Faltan datos del aviso." }, 400);

  const alert = {
    id: crypto.randomUUID(),
    user_id: userId,
    kind,
    phone: text(body.phone, 30),
    customer_name: text(body.customer_name, 120),
    summary,
    requested_date: text(body.requested_date, 10),
    requested_time: text(body.requested_time, 5),
    status: "PENDING",
    updated_at: new Date().toISOString(),
  };
  const { error } = await db.from("bot_alerts").insert(alert);
  if (error) return json({ error: error.message }, 500);
  const who = alert.customer_name || alert.phone || "Un cliente";
  const push = await pushToUser(db, userId, {
    title: titles[kind],
    body: `${who}: ${summary}`.slice(0, 240),
    url: "/#bot",
    tag: alert.id,
  });
  return json({ id: alert.id, push });
}
