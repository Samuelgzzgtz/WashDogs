# Washdog — Bot WhatsApp + Migración Supabase
**Fecha:** 2026-05-08  
**Sub-proyecto:** B — Bot de WhatsApp reactivo + base de datos compartida  
**Estado:** Aprobado

---

## Objetivo

Construir un bot de WhatsApp reactivo (el cliente inicia) usando Twilio + Node.js que agenda citas automáticamente, responde preguntas frecuentes y permite a Melina tomar el control de cualquier conversación en cualquier momento. La app Android se migra de DataStore local a Supabase para que bot y app compartan la misma fuente de datos.

---

## Sección 1 — Arquitectura General

```
Cliente WhatsApp
      ↓ mensaje
Twilio (sandbox → producción)
      ↓ webhook POST /webhook
Servidor Node.js (Express) — washdog-bot/
      ├── intentDetector.js   — clasifica el mensaje por keywords
      ├── flowEngine.js       — máquina de estados de conversación
      ├── operatorRouter.js   — comandos de Melina (!pausa, !bot, !cita)
      └── twilioSender.js     — envía respuestas vía Twilio REST API
            ↓ ↑
         Supabase (PostgreSQL)
    ┌──────────────────────────────────┐
    │ clients · appointments           │
    │ transactions · sessions          │
    │ templates · business_hours       │
    │ zone_prices                      │
    └──────────────────────────────────┘
            ↑
    App Android (Kotlin)
    AppRepository migrado de DataStore → Supabase REST SDK
```

**Flujo normal:**
1. Cliente escribe → Twilio dispara webhook → `flowEngine` lee `sessions` en Supabase → responde
2. Al confirmar cita → bot escribe en `appointments` → la app Android la ve de inmediato

**Flujo Melina:**
- Bot notifica a su número personal al inicio de cada sesión nueva
- Melina envía comandos al número del bot desde su teléfono para controlar conversaciones

---

## Sección 2 — Esquema Supabase

```sql
-- Clientes
CREATE TABLE clients (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_name       text NOT NULL,
  pet_name         text NOT NULL,
  breed            text DEFAULT '',
  phone            text DEFAULT '',
  address          text DEFAULT '',
  zone             text DEFAULT 'FAR',   -- NEAR/CLOSE/MEDIUM/FAR
  pet_type         text DEFAULT 'DOG',   -- DOG/CAT
  last_visit       date,
  needs_reschedule boolean DEFAULT false
);

-- Citas
CREATE TABLE appointments (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id        uuid REFERENCES clients(id) ON DELETE CASCADE,
  client_name      text NOT NULL,        -- desnormalizado para display rápido
  pet_name         text NOT NULL,
  service          text NOT NULL,
  date             date NOT NULL,
  time             text NOT NULL,        -- "HH:mm"
  end_time         text DEFAULT '',
  status           text DEFAULT 'SCHEDULED', -- SCHEDULED/DONE/NEXT/REMINDER
  service_location text DEFAULT 'HOME',  -- HOME/BASE
  travel_fee       numeric DEFAULT 0,
  travel_buffer    int DEFAULT 0
);

-- Transacciones (ingresos/gastos)
CREATE TABLE transactions (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  type        text NOT NULL,             -- IN/OUT
  amount      numeric NOT NULL,
  concept     text DEFAULT '',
  date_time   text NOT NULL              -- "yyyy-MM-ddTHH:mm"
);

-- Sesiones del bot (una fila por número activo)
-- phone usa el formato exacto de Twilio: "whatsapp:+52XXXXXXXXXX"
CREATE TABLE sessions (
  phone         text PRIMARY KEY,
  state         text DEFAULT 'IDLE',
  context       jsonb DEFAULT '{}',      -- datos parciales en curso
  operator_mode boolean DEFAULT false,   -- true = Melina tomó el control
  updated_at    timestamptz DEFAULT now()
);

-- Plantillas de WhatsApp
CREATE TABLE templates (
  key   text PRIMARY KEY,                -- "welcome" / "confirm" / "reschedule"
  body  text NOT NULL
);

-- Horario de negocio (una sola fila, id=1)
CREATE TABLE business_hours (
  id          int PRIMARY KEY DEFAULT 1,
  open_time   text DEFAULT '09:00',
  close_time  text DEFAULT '19:00',
  closed_days int[] DEFAULT '{0}'        -- 0=Dom, 1=Lun … 6=Sáb
);

-- Precios por zona (una sola fila, id=1)
CREATE TABLE zone_prices (
  id            int PRIMARY KEY DEFAULT 1,
  near_fee      numeric DEFAULT 0,    near_buffer   int DEFAULT 0,
  close_fee     numeric DEFAULT 50,   close_buffer  int DEFAULT 10,
  medium_fee    numeric DEFAULT 90,   medium_buffer int DEFAULT 25,
  far_fee       numeric DEFAULT 120,  far_buffer    int DEFAULT 40
);
```

**Datos iniciales:**
```sql
INSERT INTO business_hours (id) VALUES (1) ON CONFLICT DO NOTHING;
INSERT INTO zone_prices (id) VALUES (1) ON CONFLICT DO NOTHING;
INSERT INTO templates VALUES
  ('welcome',    'Hola {{nombre}}, bienvenido a Washdog 🐾'),
  ('confirm',    'Tu cita del {{fecha}} a las {{hora}} está confirmada ✅'),
  ('reschedule', 'Hola {{nombre}}, ¿puedes reagendar tu cita?');
```

---

## Sección 3 — Flujo de Conversación del Bot

### Estados de sesión

```
IDLE → COLLECTING_NAME → COLLECTING_PET → COLLECTING_SERVICE
     → COLLECTING_DATE → COLLECTING_TIME → CONFIRMING → DONE
```

### Intenciones (detectadas en cualquier mensaje)

| Keywords | Intención | Acción |
|----------|-----------|--------|
| "cita", "agendar", "reservar", "quiero" | `BOOK` | Inicia flujo de agendado |
| "precio", "costo", "cuánto", "tarifa" | `PRICE` | Lista servicios con precios de `zone_prices` |
| "mi cita", "confirmada", "tengo cita", "horario" | `STATUS` | Busca cita próxima por teléfono |
| "horario", "horas", "días", "abren" | `HOURS` | Responde con `business_hours` |
| "dónde", "dirección", "ubicación", "cómo llegar" | `LOCATION` | Responde con dirección fija |
| "hola", "buenas", "hey", "buenos días/tardes" | `GREETING` | Saludo + menú de opciones |
| cualquier otra cosa | `UNKNOWN` | "No entendí, ¿puedo ayudarte con...?" |

La detección es case-insensitive y busca substrings. Si hay múltiples keywords, gana la primera regla en orden.

### Ejemplo de flujo completo

```
Cliente:  "Hola quiero una cita"
Bot:      "¡Hola! 🐾 Soy el asistente de Washdog.
           ¿Me das tu nombre completo?"

Cliente:  "Juan Pérez"
Bot:      "¿Cómo se llama tu mascota y qué raza es?
           (ej. Milo, golden retriever)"

Cliente:  "Milo, golden retriever"
Bot:      "¿Qué servicio necesitas?
           1️⃣ Baño · $250
           2️⃣ Baño + Corte · $350
           3️⃣ Corte · $200
           (escribe el número)"

Cliente:  "2"
Bot:      "¿Para qué fecha? (dd/mm/aaaa)"

Cliente:  "15/05/2026"
Bot:      "¿A qué hora prefieres? (ej. 10:00)"

Cliente:  "10:00"
Bot:      "Confirmando tu cita:
           👤 Juan Pérez · 🐾 Milo · ✂️ Baño+Corte
           📅 Sábado 15 mayo · ⏰ 10:00
           ¿Confirmas? (sí / no)"

Cliente:  "sí"
Bot:      "¡Listo! 🎉 Tu cita está agendada.
           Te esperamos el sábado 15 de mayo a las 10:00.
           📍 Privada Granada, Mitras Poniente"
```

### Manejo de errores

| Error | Respuesta del bot |
|-------|------------------|
| Hora fuera de horario de negocio | "Ese horario está fuera de nuestro horario (09:00–19:00). ¿Otro horario?" |
| Conflicto con cita existente | "Ese horario ya está ocupado. ¿Qué tal las [hora solicitada + 1 hora]?" |
| Fecha inválida | "No reconocí esa fecha. Usa el formato dd/mm/aaaa." |
| 3 mensajes `UNKNOWN` seguidos | Bot notifica a Melina y responde: "En un momento te atiende alguien." |
| Cliente responde "no" en CONFIRMING | "Entendido, ¿quieres cambiar algo?" → vuelve a COLLECTING_SERVICE |

---

## Sección 4 — Handoff a Melina

### Notificaciones automáticas (bot → número personal de Melina)

| Evento | Mensaje |
|--------|---------|
| Nueva sesión inicia | `🤖 Nueva conversación: +52 81 XXXX — "Hola quiero una cita"` |
| Cita confirmada | `✅ Cita agendada: Juan Pérez · Milo · Baño+Corte · 15/05 10:00` |
| 3 intentos fallidos | `⚠️ Bot sin respuesta para +52 81 XXXX. ¿Tomas tú? Escribe: !pausa NÚMERO` |

### Comandos de operador (Melina → número del bot)

```
!ver
  → Lista todas las sesiones activas con su estado actual

!pausa 8112345678
  → Activa operator_mode=true para ese número
  → Bot deja de responder a ese cliente
  → Bot reenvía los mensajes del cliente a Melina en tiempo real

!bot 8112345678
  → Desactiva operator_mode, bot retoma la conversación

!cita Juan Pérez | Milo | Baño+Corte | 2026-05-15 | 10:00 | 8112345678
  → Crea el appointment en Supabase directamente
  → Envía confirmación al cliente
  → Bot responde a Melina: "✅ Cita creada"
```

### Identificación del operador

- `OPERATOR_PHONE` en `.env` almacena el número de Melina en formato `whatsapp:+521XXXXXXXXXX`
- El webhook compara el campo `From` de cada mensaje entrante contra `OPERATOR_PHONE`
- Si coincide → `operatorRouter.js`; si no → `flowEngine.js`

### Modo operador activo

- `sessions.operator_mode = true` → bot recibe el mensaje pero no genera respuesta al cliente
- Bot reenvía el mensaje del cliente a Melina: `📨 Cliente +52 81 XXXX: "mensaje"`
- Al escribir `!bot NÚMERO`, el modo vuelve a automático y el bot saluda de nuevo al cliente

---

## Sección 5 — Migración App Android a Supabase

### Alcance del cambio

Solo se reemplaza `AppRepository.kt`. El `AppViewModel`, todas las pantallas y los modelos de datos no cambian.

| Capa | Hoy | Después |
|------|-----|---------|
| `AppRepository` | DataStore + Gson | Supabase Kotlin SDK (PostgREST) |
| `AppViewModel` | Sin cambio | Sin cambio |
| Pantallas / UI | Sin cambio | Sin cambio |
| Modelos de datos | Sin cambio | Sin cambio |

### Dependencia nueva en `build.gradle.kts`

```kotlin
implementation("io.github.jan-tennant:postgrest-kt:2.5.4")
implementation("io.github.jan-tennant:supabase-kt:2.5.4")
implementation("io.ktor:ktor-client-android:2.3.12")
```

### Estrategia de migración one-shot

```kotlin
// En AppRepository.init o en AppViewModel.loadData()
if (dataStore tiene datos && supabase está vacío) {
    subirDatosLocalesASupabase()   // migración única
    marcarMigraciónCompleta()      // flag en SharedPreferences
}
// A partir de aquí, todo va a Supabase
```

### Manejo de conectividad

- Sin internet al abrir → app muestra datos ya cargados en `mutableStateListOf` (en memoria)
- Si falla una escritura → reintento automático (3 intentos con backoff), luego Toast de error
- Sin modo offline persistente — la app requiere internet para funcionar

### Credenciales

`local.properties` (no en git):
```
supabase.url=https://xxxx.supabase.co
supabase.anonKey=eyJ...
```

Leídas en `build.gradle.kts` y expuestas como `BuildConfig.SUPABASE_URL` y `BuildConfig.SUPABASE_ANON_KEY`.

---

## Sección 6 — Despliegue y Estructura del Proyecto

### Estructura `washdog-bot/`

```
washdog-bot/
├── src/
│   ├── index.js            — Express server, POST /webhook
│   ├── intentDetector.js   — keyword matching, devuelve Intent
│   ├── flowEngine.js       — máquina de estados, lee/escribe sessions
│   ├── operatorRouter.js   — procesa comandos !pausa !bot !cita
│   ├── twilioSender.js     — wrapper de Twilio REST API
│   ├── supabaseClient.js   — cliente Supabase singleton
│   └── config.js           — process.env con validación de presencia
├── tests/
│   ├── intentDetector.test.js
│   └── flowEngine.test.js
├── .env                    — no en git
├── .env.example
├── package.json
└── README.md               — setup del sandbox de Twilio paso a paso
```

### Variables de entorno

```bash
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_WHATSAPP_NUMBER=whatsapp:+14155238886   # sandbox Twilio
OPERATOR_PHONE=whatsapp:+521XXXXXXXXXX         # número personal de Melina
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_SERVICE_KEY=eyJ...                    # service role key (solo en servidor)
PORT=3000
```

### Desarrollo (sandbox Twilio)

```bash
# Terminal 1
node src/index.js

# Terminal 2
ngrok http 3000
# Copiar la URL https://xxxx.ngrok.io/webhook a Twilio Sandbox settings
```

### Producción

- **Railway** o **Render** (tier gratuito) — push a `main` dispara redeploy automático
- Variables de entorno configuradas en el dashboard de la plataforma
- El número de Twilio se apunta al URL de producción en lugar de ngrok

### Tests

- Jest para `intentDetector` y `flowEngine` (lógica pura, sin red)
- Prueba manual del flujo completo con el sandbox de Twilio

---

## Archivos afectados

| Archivo / Directorio | Cambio |
|---------------------|--------|
| `washdog-bot/` | Nuevo directorio completo |
| `washdog-native/app/build.gradle.kts` | +supabase-kt, +BuildConfig fields |
| `washdog-native/local.properties` | +supabase.url, +supabase.anonKey |
| `washdog-native/app/.../data/AppRepository.kt` | Reemplazar DataStore por Supabase SDK |
| `washdog-native/app/.../AndroidManifest.xml` | +INTERNET permission (si no existe) |

---

## Fuera de scope

- Integración Google Maps para tráfico real
- Notificaciones push a Melina (solo WhatsApp)
- Panel web de administración
- Soporte multi-operador (solo Melina)
- Migración a Meta Business API (se hará cuando salga del sandbox)
