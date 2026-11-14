import { asUser, bearer, configureWebPush, json, pushToUser } from "./_push.js";

/** Notificación de prueba para los dispositivos del usuario que la pide. */
export async function POST(request: Request) {
  const missing = configureWebPush();
  if (missing) return json({ error: missing }, 500);
  const token = bearer(request);
  if (!token) return json({ error: "Inicia sesión." }, 401);
  const db = asUser(token);
  const { data, error } = await db.auth.getUser(token);
  if (error || !data.user) return json({ error: "Sesión no válida." }, 401);
  const result = await pushToUser(db, data.user.id, {
    title: "🐶 Avisos de Washdog activados",
    body: "Aquí te van a llegar los clientes que quieren agendar o que piden para hoy.",
    url: "/#bot",
    tag: "washdog-test",
  });
  return json(result);
}
