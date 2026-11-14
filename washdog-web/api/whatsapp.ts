import { createHmac, timingSafeEqual } from "node:crypto";
import Anthropic from "@anthropic-ai/sdk";
import { asService, configureWebPush, json, pushToUser } from "./_push.js";
import {
  handleMessage,
  type AlertInput,
  type ChatTurn,
} from "../src/bot/brain.js";
import { withDefaults } from "../src/bot/config.js";

/**
 * Webhook de la API oficial de WhatsApp (Meta Cloud API).
 *  GET  → verificación de Meta (hub.challenge).
 *  POST → mensajes entrantes: el bot contesta y, si hace falta, deja aviso en la app.
 *
 * Variables: WHATSAPP_TOKEN, WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_VERIFY_TOKEN,
 * WHATSAPP_APP_SECRET, BOT_OWNER_ID, ANTHROPIC_API_KEY, SUPABASE_SERVICE_ROLE_KEY.
 */
const env = (name: string) => process.env[name]?.trim() ?? "";
const GRAPH = () =>
  `https://graph.facebook.com/${env("WHATSAPP_API_VERSION") || "v23.0"}`;

export function GET(request: Request) {
  const url = new URL(request.url);
  const verify = env("WHATSAPP_VERIFY_TOKEN");
  if (
    verify &&
    url.searchParams.get("hub.mode") === "subscribe" &&
    url.searchParams.get("hub.verify_token") === verify
  )
    return new Response(url.searchParams.get("hub.challenge") ?? "", {
      status: 200,
    });
  return new Response("Forbidden", { status: 403 });
}

function validSignature(raw: string, header: string | null): boolean {
  const secret = env("WHATSAPP_APP_SECRET");
  if (!secret || !header?.startsWith("sha256=")) return false;
  const expected = Buffer.from(
    createHmac("sha256", secret).update(raw).digest("hex"),
  );
  const received = Buffer.from(header.slice(7));
  return (
    expected.length === received.length && timingSafeEqual(expected, received)
  );
}

type WaMessage = {
  id: string;
  from: string;
  timestamp?: string;
  type: string;
  text?: { body?: string };
  button?: { text?: string };
  interactive?: {
    button_reply?: { title?: string };
    list_reply?: { title?: string };
  };
};
type WaPayload = {
  entry?: {
    changes?: {
      value?: {
        messages?: WaMessage[];
        contacts?: { wa_id?: string; profile?: { name?: string } }[];
      };
    }[];
  }[];
};

/** Hora actual de Monterrey como Date cuyos campos locales son los de Monterrey. */
function monterreyNow(): Date {
  return new Date(
    new Date().toLocaleString("en-US", { timeZone: "America/Monterrey" }),
  );
}

/** WhatsApp entrega los celulares de México como 521XXXXXXXXXX, pero para enviar espera 52XXXXXXXXXX. */
export function waRecipient(from: string): string {
  return from.replace(/^521(\d{10})$/, "52$1");
}

async function sendText(to: string, body: string) {
  const response = await fetch(
    `${GRAPH()}/${env("WHATSAPP_PHONE_NUMBER_ID")}/messages`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${env("WHATSAPP_TOKEN")}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        messaging_product: "whatsapp",
        to: waRecipient(to),
        type: "text",
        text: { body, preview_url: false },
      }),
    },
  );
  if (!response.ok)
    throw new Error(
      `WhatsApp ${response.status}: ${(await response.text()).slice(0, 300)}`,
    );
}

const kindTitle: Record<AlertInput["kind"], string> = {
  BOOKING: "📅 Quieren agendar",
  SAME_DAY: "⚡ Piden cita para hoy",
  OUT_OF_ZONE: "📍 Cliente fuera de zona",
  REVIEW: "🔎 Revisar este chat",
};

export async function POST(request: Request) {
  const raw = await request.text();
  if (!validSignature(raw, request.headers.get("x-hub-signature-256")))
    return json({ error: "Firma inválida." }, 401);
  const db = asService();
  const owner = env("BOT_OWNER_ID");
  if (!db || !owner)
    return json({ error: "Falta configurar el servidor del bot." }, 500);
  const pushReady = !configureWebPush();

  let payload: WaPayload;
  try {
    payload = JSON.parse(raw) as WaPayload;
  } catch {
    return json({ ok: true });
  }

  for (const entry of payload.entry ?? [])
    for (const change of entry.changes ?? []) {
      const value = change.value;
      for (const message of value?.messages ?? []) {
        const phone = message.from;
        const profileName =
          value?.contacts
            ?.find((c) => c.wa_id === phone)
            ?.profile?.name?.slice(0, 120) ?? "";
        // Meta reintenta: si ya guardamos este mensaje, no lo contestamos otra vez.
        const text =
          message.text?.body ??
          message.button?.text ??
          message.interactive?.button_reply?.title ??
          message.interactive?.list_reply?.title ??
          "";
        const stored = await db.from("bot_messages").insert({
          id: message.id,
          user_id: owner,
          phone,
          role: "user",
          text: text || `[${message.type}]`,
        });
        if (stored.error) {
          if (stored.error.code === "23505") continue;
          throw stored.error;
        }

        const notify = async (alert: AlertInput) => {
          const id = crypto.randomUUID();
          const { error } = await db.from("bot_alerts").insert({
            id,
            user_id: owner,
            kind: alert.kind,
            phone,
            customer_name: alert.customer_name || profileName,
            summary: alert.summary,
            requested_date: alert.requested_date,
            requested_time: alert.requested_time,
            status: "PENDING",
            updated_at: new Date().toISOString(),
          });
          if (error) throw error;
          if (pushReady)
            await pushToUser(db, owner, {
              title: kindTitle[alert.kind],
              body: `${alert.customer_name || profileName || phone}: ${alert.summary}`.slice(
                0,
                240,
              ),
              url: "/#bot",
              tag: id,
            });
        };

        let reply: string | null;
        try {
          if (!text) {
            // Foto, audio, ubicación…: que lo vea una persona.
            await notify({
              kind: "REVIEW",
              summary: `Mandó ${message.type === "image" ? "una foto" : `un mensaje de tipo ${message.type}`}. Revisa el chat.`,
              requested_date: "",
              requested_time: "",
              customer_name: profileName,
            });
            reply =
              message.type === "image"
                ? "¡Gracias por la foto! 🙌 Déjame revisarla y en un momento te confirmo."
                : "¡Gracias! Déjame revisarlo y en un momento te respondo 🙌";
          } else {
            const since = new Date(Date.now() - 48 * 3600 * 1000).toISOString();
            const today = new Date(Date.now() - 24 * 3600 * 1000)
              .toISOString()
              .slice(0, 10);
            const [config, clients, appointments, history] = await Promise.all([
              db
                .from("bot_config")
                .select("config")
                .eq("user_id", owner)
                .maybeSingle(),
              db.from("clients").select("*").eq("user_id", owner),
              db
                .from("appointments")
                .select("*")
                .eq("user_id", owner)
                .gte("date", today),
              db
                .from("bot_messages")
                .select("role, text, id")
                .eq("user_id", owner)
                .eq("phone", phone)
                .gte("created_at", since)
                .order("created_at", { ascending: true })
                .limit(30),
            ]);
            for (const r of [config, clients, appointments, history])
              if (r.error) throw r.error;
            const turns: ChatTurn[] = (history.data ?? [])
              .filter((m: { id: string }) => m.id !== message.id)
              .map((m: { role: ChatTurn["role"]; text: string }) => ({
                role: m.role,
                text: m.text,
              }));
            const anthropic = new Anthropic({
              apiKey: env("ANTHROPIC_API_KEY"),
            });
            const result = await handleMessage({
              config: withDefaults(config.data?.config),
              clients: clients.data ?? [],
              appointments: appointments.data ?? [],
              history: turns,
              text,
              phone,
              profileName,
              now: monterreyNow(),
              createMessage: (params) => anthropic.messages.create(params),
              notify,
            });
            reply = result.reply;
          }
        } catch (error) {
          // Ya guardamos el mensaje (Meta no lo reenviará): que lo vea una persona.
          console.error("bot error", error);
          reply = null;
          await notify({
            kind: "REVIEW",
            summary:
              `El bot tuvo un error con este mensaje: ${text || `[${message.type}]`}`.slice(
                0,
                1500,
              ),
            requested_date: "",
            requested_time: "",
            customer_name: profileName,
          }).catch((e) => console.error("notify error", e));
        }

        if (reply) {
          await sendText(phone, reply);
          await db.from("bot_messages").insert({
            id: `out-${message.id}`,
            user_id: owner,
            phone,
            role: "assistant",
            text: reply,
          });
        }
      }
    }
  return json({ ok: true });
}
