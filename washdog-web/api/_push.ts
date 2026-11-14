import webpush from "web-push";
import { createClient, type SupabaseClient } from "@supabase/supabase-js";

/** Lo que muestra la notificación en el teléfono. */
export type PushPayload = { title: string; body: string; url?: string; tag?: string };

const env = (name: string) => process.env[name]?.trim() ?? "";
export const supabaseUrl = () => env("SUPABASE_URL") || env("VITE_SUPABASE_URL");
export const anonKey = () => env("SUPABASE_ANON_KEY") || env("VITE_SUPABASE_ANON_KEY");

export function configureWebPush(): string | null {
  const publicKey = env("VAPID_PUBLIC_KEY") || env("VITE_VAPID_PUBLIC_KEY");
  const privateKey = env("VAPID_PRIVATE_KEY");
  if (!publicKey || !privateKey) return "Faltan las llaves VAPID en el servidor.";
  webpush.setVapidDetails(env("VAPID_SUBJECT") || "mailto:washdog@example.com", publicKey, privateKey);
  return null;
}

/** Cliente de Supabase que actúa como el usuario dueño del token (respeta RLS). */
export function asUser(token: string): SupabaseClient {
  return createClient(supabaseUrl(), anonKey(), {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${token}` } },
  });
}

/** Cliente con la llave de servicio, solo para el bot (no hay usuario con sesión). */
export function asService(): SupabaseClient | null {
  const key = env("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl() || !key) return null;
  return createClient(supabaseUrl(), key, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

/**
 * Manda la notificación a todos los dispositivos del usuario. Los dispositivos que
 * ya no existen (desinstalaron la app o quitaron el permiso) se borran solos.
 */
export async function pushToUser(db: SupabaseClient, userId: string, payload: PushPayload) {
  const { data, error } = await db
    .from("push_subscriptions")
    .select("id, endpoint, p256dh, auth")
    .eq("user_id", userId);
  if (error) throw error;
  let sent = 0;
  const gone: string[] = [];
  await Promise.all(
    (data ?? []).map(async (sub) => {
      try {
        await webpush.sendNotification(
          { endpoint: sub.endpoint, keys: { p256dh: sub.p256dh, auth: sub.auth } },
          JSON.stringify(payload),
          { TTL: 60 * 60 * 24, urgency: "high" },
        );
        sent++;
      } catch (err) {
        const status = (err as { statusCode?: number }).statusCode;
        if (status === 404 || status === 410) gone.push(sub.id);
      }
    }),
  );
  if (gone.length) await db.from("push_subscriptions").delete().in("id", gone);
  return { devices: data?.length ?? 0, sent, removed: gone.length };
}

export const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });

export function bearer(request: Request): string {
  return /^Bearer\s+(.+)$/i.exec(request.headers.get("authorization") ?? "")?.[1] ?? "";
}
