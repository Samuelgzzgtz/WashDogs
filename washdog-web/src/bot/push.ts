import { supabase } from "../data/supabase";

export const vapidPublicKey = import.meta.env.VITE_VAPID_PUBLIC_KEY?.trim() ?? "";

export type PushState =
  | "unsupported" // El navegador no tiene notificaciones (iPhone: hay que instalar la app primero).
  | "needs-install" // iPhone/iPad con Safari sin instalar en la pantalla de inicio.
  | "denied" // Se bloqueó el permiso.
  | "off" // Se puede activar.
  | "on"; // Este dispositivo ya recibe avisos.

const isIos = () => /iphone|ipad|ipod/i.test(navigator.userAgent);
const isStandalone = () =>
  window.matchMedia?.("(display-mode: standalone)").matches ||
  (navigator as Navigator & { standalone?: boolean }).standalone === true;

export async function pushState(): Promise<PushState> {
  if (isIos() && !isStandalone()) return "needs-install";
  if (!("serviceWorker" in navigator) || !("PushManager" in window) || !("Notification" in window))
    return "unsupported";
  if (Notification.permission === "denied") return "denied";
  const registration = await navigator.serviceWorker.getRegistration();
  const subscription = await registration?.pushManager.getSubscription();
  return subscription && Notification.permission === "granted" ? "on" : "off";
}

function keyBytes(base64: string): Uint8Array<ArrayBuffer> {
  const padded = (base64 + "=".repeat((4 - (base64.length % 4)) % 4))
    .replace(/-/g, "+")
    .replace(/_/g, "/");
  const raw = atob(padded);
  const bytes = new Uint8Array(new ArrayBuffer(raw.length));
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

async function endpointId(endpoint: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(endpoint));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Pide permiso, suscribe este dispositivo y lo guarda para que el servidor le pueda avisar. */
export async function enablePush(uid: string): Promise<void> {
  if (!supabase) throw new Error("La app no está conectada.");
  if (!vapidPublicKey) throw new Error("Falta configurar la llave de notificaciones (VAPID).");
  const permission = await Notification.requestPermission();
  if (permission !== "granted")
    throw new Error("No se dio permiso para notificaciones. Actívalo en los ajustes del teléfono.");
  const registration = await navigator.serviceWorker.ready;
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: keyBytes(vapidPublicKey),
    }));
  const json = subscription.toJSON();
  const { error } = await supabase.from("push_subscriptions").upsert({
    id: await endpointId(subscription.endpoint),
    user_id: uid,
    endpoint: subscription.endpoint,
    p256dh: json.keys?.p256dh ?? "",
    auth: json.keys?.auth ?? "",
    user_agent: navigator.userAgent.slice(0, 300),
    created_at: new Date().toISOString(),
  });
  if (error) throw error;
}

/** Deja de recibir avisos en este dispositivo. */
export async function disablePush(): Promise<void> {
  const registration = await navigator.serviceWorker.getRegistration();
  const subscription = await registration?.pushManager.getSubscription();
  if (!subscription) return;
  if (supabase)
    await supabase
      .from("push_subscriptions")
      .delete()
      .eq("id", await endpointId(subscription.endpoint));
  await subscription.unsubscribe();
}

/** Pide al servidor una notificación de prueba para todos tus dispositivos. */
export async function sendTestPush(): Promise<{ devices: number; sent: number }> {
  if (!supabase) throw new Error("La app no está conectada.");
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  if (!token) throw new Error("Inicia sesión de nuevo.");
  const response = await fetch("/api/push-test", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = (await response.json().catch(() => ({}))) as {
    error?: string;
    devices?: number;
    sent?: number;
  };
  if (!response.ok) throw new Error(body.error || "No se pudo mandar la prueba.");
  return { devices: body.devices ?? 0, sent: body.sent ?? 0 };
}
