# Washdog Bot

Bot de WhatsApp reactivo para Washdog Grooming. Usa Twilio + Supabase.

## Setup local

1. `npm install`
2. `cp .env.example .env` y rellenar con credenciales
3. `npm run dev`
4. En otra terminal: `ngrok http 3000`
5. Copiar la URL `https://xxxx.ngrok.io/webhook` en Twilio Sandbox Settings
   → Twilio Console → Messaging → Try it out → Send a WhatsApp message
   → "When a message comes in": pegar la URL ngrok

## Comandos de operador (desde el número de Melina)

| Comando | Acción |
|---------|--------|
| `!ver` | Lista sesiones activas |
| `!pausa 8112345678` | Melina toma el control |
| `!bot 8112345678` | Devuelve control al bot |
| `!cita nombre \| mascota \| servicio \| 2026-05-15 \| 10:00 \| 8112345678` | Agenda directamente |

## Deploy en Railway / Render

1. Push a `main`
2. En el dashboard: conectar repo, set working directory = `washdog-bot`
3. Agregar variables de entorno del `.env`
4. Actualizar webhook de Twilio al URL de producción
