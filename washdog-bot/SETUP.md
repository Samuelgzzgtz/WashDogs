# Washdog Bot — Guía de configuración

## Cómo funciona

### Arquitectura

```
Cliente WhatsApp
      ↓ mensaje
   Twilio
      ↓ HTTP POST
  /webhook (Node.js + Express)
      ↓
  ¿Es Melina (operadora)?
  ├─ SÍ → operatorRouter (comandos !ver, !pausa, !bot, !cita)
  └─ NO → flowEngine (conversación con el cliente)
      ↓
  Supabase (base de datos compartida con la app Android)
```

### Flujo con el cliente

El bot es reactivo — el cliente escribe primero. El estado de cada conversación se guarda en Supabase.

| Estado | Qué espera | Qué responde |
|--------|-----------|-------------|
| `IDLE` | Cualquier mensaje | Menú: 1 Cita · 2 Precios · 3 Horario · 4 Ubicación |
| `COLLECTING_NAME` | Nombre completo | Pregunta por mascota y raza |
| `COLLECTING_PET` | "Milo, golden retriever" | Muestra menú de servicios |
| `COLLECTING_SERVICE` | Número (1/2/3) | Pregunta la fecha |
| `COLLECTING_DATE` | dd/mm/aaaa | Pregunta la hora |
| `COLLECTING_TIME` | HH:MM | Verifica horario y conflictos, muestra resumen |
| `CONFIRMING` | "sí" / "no" | Agenda en Supabase o cancela |
| `DONE` | Cualquier mensaje nuevo | Regresa al menú |

Si el cliente escribe 3 cosas que el bot no entiende, escala a Melina automáticamente.

### Comandos de Melina

Melina los envía por WhatsApp desde su número registrado como `OPERATOR_PHONE`:

| Comando | Qué hace |
|---------|---------|
| `!ver` | Lista todas las sesiones activas |
| `!pausa 8112345678` | Silencia el bot para ese cliente y reenvía sus mensajes a Melina |
| `!bot 8112345678` | Reactiva el bot para ese cliente |
| `!cita nombre \| mascota \| servicio \| 2026-06-01 \| 10:00 \| 8112345678` | Crea una cita directamente |

---

## Paso a paso para activarlo

### 1. Cuenta Twilio

1. Ir a [twilio.com](https://twilio.com) y crear cuenta (hay plan gratuito)
2. Ir a **Messaging → Try it out → Send a WhatsApp message**
3. El sandbox asigna un número (ej. `+14155238886`) y una contraseña de activación tipo `join word-word`
4. Tanto el número de prueba como el de Melina deben enviar esa contraseña al número sandbox para activarlo
5. Copiar el **Account SID** y el **Auth Token** del dashboard principal

### 2. Llenar el archivo `.env`

Abrir `washdog-bot/.env` y completar con los valores reales:

```
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_WHATSAPP_NUMBER=whatsapp:+14155238886
OPERATOR_PHONE=whatsapp:+521XXXXXXXXXX
SUPABASE_URL=https://ristcoimdzucocvttefs.supabase.co
SUPABASE_SERVICE_KEY=eyJ...
PORT=3000
```

> `OPERATOR_PHONE` es el número de Melina con prefijo de México: `whatsapp:+521` seguido de los 10 dígitos.

### 3. Instalar dependencias

```bash
cd washdog-bot
npm install
```

### 4. Exponer el servidor con ngrok (desarrollo)

Twilio necesita una URL pública para enviar los mensajes. En desarrollo se usa ngrok:

```bash
# Descargar ngrok desde https://ngrok.com/download e instalar

# Terminal 1 — arrancar el bot:
cd washdog-bot
npm start

# Terminal 2 — exponer el puerto 3000:
ngrok http 3000
```

Ngrok muestra una URL pública como `https://abc123.ngrok-free.app`.

### 5. Configurar el webhook en Twilio

1. Ir a **Messaging → Try it out → WhatsApp Sandbox Settings**
2. En el campo **"When a message comes in"** poner: `https://abc123.ngrok-free.app/webhook`
3. Método: `HTTP POST`
4. Guardar

### 6. Probar

Desde WhatsApp escribirle al número sandbox: `hola`
El bot debe responder con el menú de 4 opciones.

---

## Producción (cuando se quiera lanzar de verdad)

Para producción hay dos cambios respecto al desarrollo:

**Servidor permanente** — En lugar de correr el bot en la computadora con ngrok, desplegarlo en un servicio en la nube. Opciones gratuitas o baratas:
- [Railway](https://railway.app) — el más fácil, conecta directo con GitHub
- [Render](https://render.com) — plan gratuito disponible
- [Fly.io](https://fly.io) — buen rendimiento

El comando de arranque es `npm start` y el puerto es `3000` (ya configurado).

**Número de WhatsApp Business real** — El sandbox de Twilio es solo para pruebas. Para clientes reales hay que registrar un número de WhatsApp Business con Twilio, lo cual requiere aprobación de Meta (puede tomar unos días). Ver: [twilio.com/whatsapp](https://www.twilio.com/en-us/whatsapp).
