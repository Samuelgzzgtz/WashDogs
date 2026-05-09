# Washdog Bot WhatsApp + Migración Supabase — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reactive WhatsApp bot (Node.js + Twilio) that auto-books appointments and lets Melina take control at any time, backed by Supabase as the shared database with the Android app.

**Architecture:** Node.js Express server receives Twilio webhooks, classifies messages by keywords (IntentDetector), drives a per-conversation state machine (FlowEngine), and routes operator commands from Melina (OperatorRouter). The Android app's AppRepository is replaced to call Supabase REST API via OkHttp instead of local DataStore.

**Tech Stack:** Node.js 20 · Express 4 · Twilio WhatsApp API · @supabase/supabase-js 2 · Jest (unit tests) · Kotlin + OkHttp 4 + Gson (Android) · Supabase PostgreSQL

---

## File Map

| Acción | Archivo |
|--------|---------|
| Crear | `washdog-bot/package.json` |
| Crear | `washdog-bot/src/config.js` |
| Crear | `washdog-bot/src/supabaseClient.js` |
| Crear | `washdog-bot/src/twilioSender.js` |
| Crear | `washdog-bot/src/intentDetector.js` |
| Crear | `washdog-bot/src/flowEngine.js` |
| Crear | `washdog-bot/src/operatorRouter.js` |
| Crear | `washdog-bot/src/index.js` |
| Crear | `washdog-bot/tests/intentDetector.test.js` |
| Crear | `washdog-bot/tests/flowEngine.test.js` |
| Crear | `washdog-bot/.env.example` |
| Crear | `washdog-bot/README.md` |
| Modificar | `washdog-native/app/build.gradle.kts` |
| Modificar | `washdog-native/local.properties` |
| Modificar | `washdog-native/app/src/main/AndroidManifest.xml` |
| Crear | `washdog-native/app/src/main/java/com/washdog/app/data/SupabaseDataSource.kt` |
| Reemplazar | `washdog-native/app/src/main/java/com/washdog/app/data/AppRepository.kt` |

Todos los paths del bot son relativos a la raíz del repo. Los de Android son relativos a la raíz del repo.

---

## Fase A — Supabase

### Task 1: Crear proyecto Supabase y ejecutar schema

**Files:** Ninguno (pasos manuales en supabase.com)

- [ ] **Step 1: Crear proyecto en Supabase**

1. Ir a [https://supabase.com](https://supabase.com) → "New project"
2. Organization: la existente
3. Name: `washdog`
4. Database password: generar y guardar en lugar seguro
5. Region: `us-east-1` (o el más cercano a México)
6. Esperar ~2 minutos a que el proyecto esté listo

- [ ] **Step 2: Ejecutar schema en el SQL Editor**

En Supabase → SQL Editor → New query, pegar y ejecutar:

```sql
-- Clientes
CREATE TABLE clients (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_name       text NOT NULL,
  pet_name         text NOT NULL,
  breed            text DEFAULT '',
  phone            text DEFAULT '',
  address          text DEFAULT '',
  zone             text DEFAULT 'FAR',
  pet_type         text DEFAULT 'DOG',
  last_visit       date,
  needs_reschedule boolean DEFAULT false
);

-- Citas (client_id nullable para compatibilidad con migración)
CREATE TABLE appointments (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id        uuid,
  client_name      text NOT NULL,
  pet_name         text NOT NULL,
  service          text NOT NULL,
  date             date NOT NULL,
  time             text NOT NULL,
  end_time         text DEFAULT '',
  status           text DEFAULT 'SCHEDULED',
  service_location text DEFAULT 'HOME',
  travel_fee       numeric DEFAULT 0,
  travel_buffer    int DEFAULT 0
);

-- Transacciones
CREATE TABLE transactions (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  type        text NOT NULL,
  amount      numeric NOT NULL,
  concept     text DEFAULT '',
  date_time   text NOT NULL
);

-- Sesiones del bot
CREATE TABLE sessions (
  phone         text PRIMARY KEY,
  state         text DEFAULT 'IDLE',
  context       jsonb DEFAULT '{}',
  operator_mode boolean DEFAULT false,
  updated_at    timestamptz DEFAULT now()
);

-- Plantillas WhatsApp
CREATE TABLE templates (
  key   text PRIMARY KEY,
  body  text NOT NULL
);

-- Horario de negocio (una sola fila)
CREATE TABLE business_hours (
  id          int PRIMARY KEY DEFAULT 1,
  open_time   text DEFAULT '09:00',
  close_time  text DEFAULT '19:00',
  closed_days int[] DEFAULT '{0}'
);

-- Precios por zona (una sola fila)
CREATE TABLE zone_prices (
  id            int PRIMARY KEY DEFAULT 1,
  near_fee      numeric DEFAULT 0,    near_buffer   int DEFAULT 0,
  close_fee     numeric DEFAULT 50,   close_buffer  int DEFAULT 10,
  medium_fee    numeric DEFAULT 90,   medium_buffer int DEFAULT 25,
  far_fee       numeric DEFAULT 120,  far_buffer    int DEFAULT 40
);
```

- [ ] **Step 3: Ejecutar datos iniciales**

```sql
INSERT INTO business_hours (id) VALUES (1) ON CONFLICT DO NOTHING;
INSERT INTO zone_prices (id) VALUES (1) ON CONFLICT DO NOTHING;
INSERT INTO templates VALUES
  ('welcome',    'Hola {{nombre}}, bienvenido a Washdog 🐾'),
  ('confirm',    'Tu cita del {{fecha}} a las {{hora}} está confirmada ✅'),
  ('reschedule', 'Hola {{nombre}}, ¿puedes reagendar tu cita?')
ON CONFLICT DO NOTHING;
```

- [ ] **Step 4: Copiar credenciales**

En Supabase → Project Settings → API:
- Copiar **Project URL** (ej. `https://xxxx.supabase.co`)
- Copiar **anon public key** (para la app Android)
- Copiar **service_role secret key** (solo para el bot Node.js — nunca en la app)

Guardar temporalmente; se usarán en Tasks 2 y 9.

---

## Fase B — Bot Node.js

### Task 2: Scaffold washdog-bot — package.json, config.js, .env.example

**Files:**
- Create: `washdog-bot/package.json`
- Create: `washdog-bot/src/config.js`
- Create: `washdog-bot/.env.example`
- Create: `washdog-bot/README.md`

- [ ] **Step 1: Crear `washdog-bot/package.json`**

```json
{
  "name": "washdog-bot",
  "version": "1.0.0",
  "description": "WhatsApp bot reactivo para Washdog Grooming",
  "main": "src/index.js",
  "scripts": {
    "start": "node src/index.js",
    "dev": "node --watch src/index.js",
    "test": "jest --testPathPattern=tests/"
  },
  "dependencies": {
    "@supabase/supabase-js": "^2.44.4",
    "dotenv": "^16.4.5",
    "express": "^4.19.2",
    "twilio": "^5.2.2"
  },
  "devDependencies": {
    "jest": "^29.7.0"
  }
}
```

- [ ] **Step 2: Instalar dependencias**

```bash
cd washdog-bot
npm install
```

Expected: `node_modules/` creado, sin errores.

- [ ] **Step 3: Crear `washdog-bot/src/config.js`**

```javascript
require('dotenv').config()

const required = [
  'TWILIO_ACCOUNT_SID',
  'TWILIO_AUTH_TOKEN',
  'TWILIO_WHATSAPP_NUMBER',
  'OPERATOR_PHONE',
  'SUPABASE_URL',
  'SUPABASE_SERVICE_KEY',
]

for (const key of required) {
  if (!process.env[key]) throw new Error(`Missing env var: ${key}`)
}

module.exports = {
  twilio: {
    accountSid: process.env.TWILIO_ACCOUNT_SID,
    authToken:  process.env.TWILIO_AUTH_TOKEN,
    from:       process.env.TWILIO_WHATSAPP_NUMBER,
  },
  operatorPhone: process.env.OPERATOR_PHONE,
  supabase: {
    url:        process.env.SUPABASE_URL,
    serviceKey: process.env.SUPABASE_SERVICE_KEY,
  },
  port: parseInt(process.env.PORT) || 3000,
}
```

- [ ] **Step 4: Crear `washdog-bot/.env.example`**

```bash
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_WHATSAPP_NUMBER=whatsapp:+14155238886
OPERATOR_PHONE=whatsapp:+521XXXXXXXXXX
SUPABASE_URL=https://xxxx.supabase.co
SUPABASE_SERVICE_KEY=eyJ...
PORT=3000
```

- [ ] **Step 5: Crear `washdog-bot/.env`** (no se commitea)

Copiar `.env.example` a `.env` y rellenar con las credenciales reales de Task 1.

```bash
cp .env.example .env
# Editar .env con valores reales
```

- [ ] **Step 6: Verificar que `.gitignore` del repo excluye `.env`**

En la raíz del repo, verificar que `.gitignore` contiene `*.env` o `.env`. Si no existe `.gitignore` en `washdog-bot/`, crearlo:

```
node_modules/
.env
```

- [ ] **Step 7: Crear `washdog-bot/README.md`**

```markdown
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
```

- [ ] **Step 8: Commit**

```bash
git add washdog-bot/
git commit -m "feat: scaffold washdog-bot with package.json, config and README"
```

---

### Task 3: supabaseClient.js + twilioSender.js

**Files:**
- Create: `washdog-bot/src/supabaseClient.js`
- Create: `washdog-bot/src/twilioSender.js`

- [ ] **Step 1: Crear `washdog-bot/src/supabaseClient.js`**

```javascript
const { createClient } = require('@supabase/supabase-js')
const config = require('./config')

module.exports = createClient(config.supabase.url, config.supabase.serviceKey)
```

- [ ] **Step 2: Crear `washdog-bot/src/twilioSender.js`**

```javascript
const twilio = require('twilio')
const config = require('./config')

const client = twilio(config.twilio.accountSid, config.twilio.authToken)

async function send(to, body) {
  return client.messages.create({ from: config.twilio.from, to, body })
}

module.exports = { send }
```

- [ ] **Step 3: Verificar sintaxis**

```bash
cd washdog-bot
node -e "require('./src/supabaseClient'); console.log('ok')"
node -e "require('./src/twilioSender'); console.log('ok')"
```

Expected: `ok` dos veces (puede haber warning de credenciales vacías si `.env` no está configurado, pero no debe tirar error de sintaxis).

- [ ] **Step 4: Commit**

```bash
git add washdog-bot/src/supabaseClient.js washdog-bot/src/twilioSender.js
git commit -m "feat: add supabaseClient and twilioSender"
```

---

### Task 4: intentDetector.js — TDD

**Files:**
- Create: `washdog-bot/tests/intentDetector.test.js`
- Create: `washdog-bot/src/intentDetector.js`

- [ ] **Step 1: Crear `washdog-bot/tests/intentDetector.test.js`**

```javascript
const { detectIntent } = require('../src/intentDetector')

describe('detectIntent', () => {
  test('BOOK — "quiero una cita"', () => {
    expect(detectIntent('quiero una cita')).toBe('BOOK')
  })
  test('BOOK — "AGENDAR" (mayúsculas)', () => {
    expect(detectIntent('AGENDAR')).toBe('BOOK')
  })
  test('BOOK — "reservar turno"', () => {
    expect(detectIntent('reservar turno')).toBe('BOOK')
  })
  test('PRICE — "cuánto cuesta el baño"', () => {
    expect(detectIntent('cuánto cuesta el baño')).toBe('PRICE')
  })
  test('PRICE — "cuanto cuesta" sin acento', () => {
    expect(detectIntent('cuanto cuesta')).toBe('PRICE')
  })
  test('PRICE — "precio del corte"', () => {
    expect(detectIntent('precio del corte')).toBe('PRICE')
  })
  test('STATUS — "mi cita está confirmada"', () => {
    expect(detectIntent('mi cita está confirmada')).toBe('STATUS')
  })
  test('STATUS — "tengo cita el viernes"', () => {
    expect(detectIntent('tengo cita el viernes')).toBe('STATUS')
  })
  test('HOURS — "cuál es el horario"', () => {
    expect(detectIntent('cuál es el horario')).toBe('HOURS')
  })
  test('HOURS — "qué días abren"', () => {
    expect(detectIntent('qué días abren')).toBe('HOURS')
  })
  test('LOCATION — "dónde están"', () => {
    expect(detectIntent('dónde están')).toBe('LOCATION')
  })
  test('LOCATION — "donde estan" sin acento', () => {
    expect(detectIntent('donde estan')).toBe('LOCATION')
  })
  test('LOCATION — "cómo llegar"', () => {
    expect(detectIntent('cómo llegar')).toBe('LOCATION')
  })
  test('GREETING — "hola"', () => {
    expect(detectIntent('hola')).toBe('GREETING')
  })
  test('GREETING — "Buenos días" con mayúscula', () => {
    expect(detectIntent('Buenos días')).toBe('GREETING')
  })
  test('GREETING — "buenas tardes"', () => {
    expect(detectIntent('buenas tardes')).toBe('GREETING')
  })
  test('BOOK antes que GREETING — "hola quiero cita"', () => {
    expect(detectIntent('hola quiero cita')).toBe('BOOK')
  })
  test('UNKNOWN — mensaje irrelevante', () => {
    expect(detectIntent('gracias por todo')).toBe('UNKNOWN')
  })
  test('UNKNOWN — string vacío', () => {
    expect(detectIntent('')).toBe('UNKNOWN')
  })
})
```

- [ ] **Step 2: Correr tests — deben FALLAR**

```bash
cd washdog-bot
npm test
```

Expected: error `Cannot find module '../src/intentDetector'`

- [ ] **Step 3: Crear `washdog-bot/src/intentDetector.js`**

```javascript
// Orden importa: BOOK antes de GREETING para que "hola quiero cita" → BOOK
const RULES = [
  { intent: 'BOOK',     keywords: ['cita', 'agendar', 'reservar', 'quiero'] },
  { intent: 'PRICE',    keywords: ['precio', 'costo', 'cuánto', 'cuanto', 'tarifa'] },
  { intent: 'STATUS',   keywords: ['mi cita', 'confirmada', 'tengo cita'] },
  { intent: 'HOURS',    keywords: ['horario', 'horas', 'días', 'dias', 'abren'] },
  { intent: 'LOCATION', keywords: ['dónde', 'donde', 'dirección', 'direccion', 'ubicación', 'ubicacion', 'cómo llegar', 'como llegar'] },
  { intent: 'GREETING', keywords: ['hola', 'buenas', 'hey', 'buenos días', 'buenos dias', 'buenas tardes', 'buenas noches'] },
]

function detectIntent(message) {
  const lower = message.toLowerCase()
  for (const { intent, keywords } of RULES) {
    if (keywords.some(kw => lower.includes(kw))) return intent
  }
  return 'UNKNOWN'
}

module.exports = { detectIntent }
```

- [ ] **Step 4: Correr tests — deben PASAR todos**

```bash
cd washdog-bot
npm test
```

Expected:
```
PASS tests/intentDetector.test.js
  detectIntent
    ✓ BOOK — "quiero una cita"
    ...
    ✓ UNKNOWN — string vacío
Test Suites: 1 passed
Tests:       19 passed
```

- [ ] **Step 5: Commit**

```bash
git add washdog-bot/tests/intentDetector.test.js washdog-bot/src/intentDetector.js
git commit -m "feat: add intentDetector with TDD (19 tests)"
```

---

### Task 5: flowEngine helpers — TDD (parseDate, parseTime, isWithinBusinessHours)

**Files:**
- Create: `washdog-bot/tests/flowEngine.test.js`
- Create: `washdog-bot/src/flowEngine.js` (solo helpers por ahora)

- [ ] **Step 1: Crear `washdog-bot/tests/flowEngine.test.js`**

```javascript
const { parseDate, parseTime, isWithinBusinessHours } = require('../src/flowEngine')

describe('parseDate', () => {
  test('acepta dd/mm/yyyy válido', () => {
    expect(parseDate('15/05/2026')).toBe('2026-05-15')
  })
  test('acepta día y mes de un dígito', () => {
    expect(parseDate('1/5/2026')).toBe('2026-05-01')
  })
  test('retorna null para formato incorrecto', () => {
    expect(parseDate('2026-05-15')).toBeNull()
    expect(parseDate('15-05-2026')).toBeNull()
    expect(parseDate('texto')).toBeNull()
    expect(parseDate('')).toBeNull()
  })
  test('retorna null para fecha inválida', () => {
    expect(parseDate('32/01/2026')).toBeNull()
    expect(parseDate('01/13/2026')).toBeNull()
  })
})

describe('parseTime', () => {
  test('acepta HH:MM válido', () => {
    expect(parseTime('10:00')).toBe('10:00')
    expect(parseTime('9:30')).toBe('09:30')
  })
  test('normaliza un dígito de hora', () => {
    expect(parseTime(' 8:00 ')).toBe('08:00')
  })
  test('retorna null para formato incorrecto', () => {
    expect(parseTime('10-00')).toBeNull()
    expect(parseTime('diez')).toBeNull()
    expect(parseTime('')).toBeNull()
  })
})

describe('isWithinBusinessHours', () => {
  const bh = { open_time: '09:00', close_time: '19:00', closed_days: [0] }

  test('retorna true dentro del horario en día abierto', () => {
    // 2026-05-11 es lunes (dayOfWeek=1, no está en closedDays)
    expect(isWithinBusinessHours('10:00', '2026-05-11', bh)).toBe(true)
  })
  test('retorna false antes de apertura', () => {
    expect(isWithinBusinessHours('08:00', '2026-05-11', bh)).toBe(false)
  })
  test('retorna false después de cierre', () => {
    expect(isWithinBusinessHours('20:00', '2026-05-11', bh)).toBe(false)
  })
  test('retorna false en día cerrado (domingo = 0)', () => {
    // 2026-05-10 es domingo
    expect(isWithinBusinessHours('11:00', '2026-05-10', bh)).toBe(false)
  })
  test('retorna true exactamente en hora de apertura', () => {
    expect(isWithinBusinessHours('09:00', '2026-05-11', bh)).toBe(true)
  })
  test('retorna true exactamente en hora de cierre', () => {
    expect(isWithinBusinessHours('19:00', '2026-05-11', bh)).toBe(true)
  })
})
```

- [ ] **Step 2: Correr tests — deben FALLAR**

```bash
cd washdog-bot
npm test
```

Expected: `Cannot find module '../src/flowEngine'`

- [ ] **Step 3: Crear `washdog-bot/src/flowEngine.js`** (solo helpers por ahora)

```javascript
function parseDate(input) {
  const match = (input || '').match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/)
  if (!match) return null
  const [, d, m, y] = match
  const iso = `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`
  const date = new Date(iso + 'T12:00:00')
  if (isNaN(date.getTime())) return null
  // Verify no overflow (e.g. 32/01 → JS silently wraps)
  if (date.getMonth() + 1 !== parseInt(m)) return null
  if (date.getDate() !== parseInt(d)) return null
  return iso
}

function parseTime(input) {
  const match = (input || '').trim().match(/^(\d{1,2}):(\d{2})$/)
  if (!match) return null
  const [, h, min] = match
  if (parseInt(h) > 23 || parseInt(min) > 59) return null
  return `${h.padStart(2, '0')}:${min}`
}

function isWithinBusinessHours(time, date, bh) {
  const dow = new Date(date + 'T12:00:00').getDay() // 0=Sun
  if (bh.closed_days.includes(dow)) return false
  return time >= bh.open_time && time <= bh.close_time
}

module.exports = { parseDate, parseTime, isWithinBusinessHours }
```

- [ ] **Step 4: Correr tests — deben PASAR**

```bash
cd washdog-bot
npm test
```

Expected:
```
PASS tests/intentDetector.test.js
PASS tests/flowEngine.test.js
Tests: 19 + 13 = 32 passed
```

- [ ] **Step 5: Commit**

```bash
git add washdog-bot/tests/flowEngine.test.js washdog-bot/src/flowEngine.js
git commit -m "feat: add flowEngine helpers (parseDate, parseTime, isWithinBusinessHours) with TDD"
```

---

### Task 6: flowEngine.js — handleMessage (máquina de estados completa)

**Files:**
- Modify: `washdog-bot/src/flowEngine.js`

- [ ] **Step 1: Reemplazar `washdog-bot/src/flowEngine.js` con la versión completa**

```javascript
const supabase = require('./supabaseClient')
const { send } = require('./twilioSender')
const config   = require('./config')
const { detectIntent } = require('./intentDetector')

// ── Helpers (exportados también para tests) ───────────────────────────────────

function parseDate(input) {
  const match = (input || '').match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/)
  if (!match) return null
  const [, d, m, y] = match
  const iso = `${y}-${m.padStart(2, '0')}-${d.padStart(2, '0')}`
  const date = new Date(iso + 'T12:00:00')
  if (isNaN(date.getTime())) return null
  if (date.getMonth() + 1 !== parseInt(m)) return null
  if (date.getDate() !== parseInt(d)) return null
  return iso
}

function parseTime(input) {
  const match = (input || '').trim().match(/^(\d{1,2}):(\d{2})$/)
  if (!match) return null
  const [, h, min] = match
  if (parseInt(h) > 23 || parseInt(min) > 59) return null
  return `${h.padStart(2, '0')}:${min}`
}

function isWithinBusinessHours(time, date, bh) {
  const dow = new Date(date + 'T12:00:00').getDay()
  if (bh.closed_days.includes(dow)) return false
  return time >= bh.open_time && time <= bh.close_time
}

// ── Catálogo de servicios (hardcoded) ─────────────────────────────────────────

const SERVICES = [
  { id: 1, name: 'Baño',         price: 250 },
  { id: 2, name: 'Baño + Corte', price: 350 },
  { id: 3, name: 'Corte',        price: 200 },
]

function serviceMenu() {
  return SERVICES.map(s => `${s.id}️⃣ ${s.name} · $${s.price}`).join('\n')
}

function nextHour(time) {
  const h = parseInt(time.split(':')[0])
  return `${String(h + 1).padStart(2, '0')}:00`
}

// ── Supabase helpers ──────────────────────────────────────────────────────────

async function getSession(phone) {
  const { data } = await supabase.from('sessions').select('*').eq('phone', phone).single()
  return data ?? { phone, state: 'IDLE', context: {}, operator_mode: false }
}

async function saveSession(phone, state, context) {
  await supabase.from('sessions').upsert({ phone, state, context, updated_at: new Date().toISOString() })
}

async function getBusinessHours() {
  const { data } = await supabase.from('business_hours').select('*').eq('id', 1).single()
  return data ?? { open_time: '09:00', close_time: '19:00', closed_days: [0] }
}

async function isTimeConflict(date, time) {
  const { data } = await supabase.from('appointments').select('time').eq('date', date)
  return (data ?? []).some(a => a.time === time)
}

async function bookAppointment(ctx) {
  const service = SERVICES.find(s => s.id === parseInt(ctx.serviceId))

  // Upsert client (por teléfono)
  const cleanPhone = ctx.phone.replace('whatsapp:+52', '').replace(/\D/g, '')
  const { data: existing } = await supabase.from('clients').select('id').eq('phone', cleanPhone).maybeSingle()
  let clientId = existing?.id

  if (!clientId) {
    const { data: created } = await supabase.from('clients').insert({
      owner_name: ctx.name,
      pet_name:   ctx.petName,
      breed:      ctx.breed ?? '',
      phone:      cleanPhone,
      last_visit: ctx.date,
    }).select('id').single()
    clientId = created?.id
  } else {
    // Actualizar último visita
    await supabase.from('clients').update({ last_visit: ctx.date }).eq('id', clientId)
  }

  await supabase.from('appointments').insert({
    client_id:        clientId,
    client_name:      ctx.name,
    pet_name:         ctx.petName,
    service:          service.name,
    date:             ctx.date,
    time:             ctx.time,
    status:           'SCHEDULED',
    service_location: 'HOME',
  })
}

// ── handleMessage ─────────────────────────────────────────────────────────────

async function handleMessage(phone, message) {
  const session = await getSession(phone)

  if (session.operator_mode) {
    // Bot silencioso; reenviar mensaje a Melina
    await send(config.operatorPhone, `📨 Cliente ${phone}: "${message}"`)
    return
  }

  const { state } = session
  let ctx      = { ...session.context }
  let newState = state
  let response = null

  // ── IDLE / DONE ───────────────────────────────────────────────────────────
  if (state === 'IDLE' || state === 'DONE') {
    const lower  = message.trim().toLowerCase()
    const intent = detectIntent(message)

    // Respuesta a menú numérico
    if (lower === '1') { response = '¡Con gusto! ¿Me das tu nombre completo?'; newState = 'COLLECTING_NAME'; }
    else if (lower === '2') { response = `Nuestros servicios:\n${serviceMenu()}`; }
    else if (lower === '3') {
      const bh = await getBusinessHours()
      const days = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb']
      const closed = bh.closed_days.map(d => days[d]).join(', ')
      response = `🕐 Horario: ${bh.open_time} – ${bh.close_time}\n📅 Días cerrados: ${closed}`
    }
    else if (lower === '4') { response = '📍 Privada Granada, Mitras Poniente, Monterrey, NL.' }
    else if (intent === 'BOOK') {
      response = '¡Con gusto! ¿Me das tu nombre completo?'
      newState  = 'COLLECTING_NAME'
    }
    else if (intent === 'GREETING') {
      response = '¡Hola! 🐾 Soy el asistente de Washdog.\n\n¿En qué puedo ayudarte?\n1️⃣ Agendar cita\n2️⃣ Precios\n3️⃣ Horario\n4️⃣ Dónde estamos'
    }
    else if (intent === 'PRICE') { response = `Nuestros servicios:\n${serviceMenu()}` }
    else if (intent === 'HOURS') {
      const bh = await getBusinessHours()
      const days = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb']
      const closed = bh.closed_days.map(d => days[d]).join(', ')
      response = `🕐 Horario: ${bh.open_time} – ${bh.close_time}\n📅 Días cerrados: ${closed}`
    }
    else if (intent === 'LOCATION') { response = '📍 Privada Granada, Mitras Poniente, Monterrey, NL.' }
    else if (intent === 'STATUS') {
      const cleanPhone = phone.replace('whatsapp:+52', '').replace(/\D/g, '')
      const { data: client } = await supabase.from('clients').select('owner_name').eq('phone', cleanPhone).maybeSingle()
      if (client) {
        const today = new Date().toISOString().split('T')[0]
        const { data: appts } = await supabase.from('appointments').select('date, time, service').eq('client_name', client.owner_name).gte('date', today).order('date').limit(1)
        if (appts && appts.length > 0) {
          const a = appts[0]
          response = `Tu próxima cita:\n📅 ${a.date} ⏰ ${a.time}\n✂️ ${a.service}`
        } else {
          response = 'No encontré citas próximas. ¿Quieres agendar? Escribe "cita".'
        }
      } else {
        response = 'No encontré tu número en nuestro registro. ¿Quieres agendar? Escribe "cita".'
      }
    }
    else {
      ctx.unknownCount = (ctx.unknownCount || 0) + 1
      if (ctx.unknownCount >= 3) {
        response = 'En un momento te atiende alguien. 👋'
        await saveSession(phone, 'IDLE', { ...ctx, needsOperator: true })
        await send(config.operatorPhone, `⚠️ Bot sin respuesta para ${phone} (3 intentos). ¿Tomas tú?\nEscribe: !pausa ${phone.replace('whatsapp:+52', '')}`)
        return
      }
      response = `No entendí 😅 Puedo ayudarte con:\n• Agendar cita\n• Precios\n• Horario\n• Dónde estamos\n\nO escribe el número del menú (1-4).`
    }
  }

  // ── COLLECTING_NAME ───────────────────────────────────────────────────────
  else if (state === 'COLLECTING_NAME') {
    ctx.name = message.trim()
    response  = `¿Cómo se llama tu mascota y qué raza es?\n(ej. Milo, golden retriever)`
    newState  = 'COLLECTING_PET'
  }

  // ── COLLECTING_PET ────────────────────────────────────────────────────────
  else if (state === 'COLLECTING_PET') {
    const parts  = message.split(',')
    ctx.petName  = parts[0].trim()
    ctx.breed    = parts[1]?.trim() ?? ''
    response     = `¿Qué servicio necesitas?\n${serviceMenu()}\n(escribe el número)`
    newState     = 'COLLECTING_SERVICE'
  }

  // ── COLLECTING_SERVICE ────────────────────────────────────────────────────
  else if (state === 'COLLECTING_SERVICE') {
    const id      = parseInt(message.trim())
    const service = SERVICES.find(s => s.id === id)
    if (!service) {
      response = `Por favor escribe solo el número del servicio:\n${serviceMenu()}`
    } else {
      ctx.serviceId   = id
      ctx.serviceName = service.name
      response        = '¿Para qué fecha? (dd/mm/aaaa)'
      newState        = 'COLLECTING_DATE'
    }
  }

  // ── COLLECTING_DATE ───────────────────────────────────────────────────────
  else if (state === 'COLLECTING_DATE') {
    const iso = parseDate(message.trim())
    if (!iso) {
      response = 'No reconocí esa fecha. Usa el formato dd/mm/aaaa (ej. 15/05/2026).'
    } else {
      ctx.date = iso
      response = '¿A qué hora prefieres? (ej. 10:00)'
      newState = 'COLLECTING_TIME'
    }
  }

  // ── COLLECTING_TIME ───────────────────────────────────────────────────────
  else if (state === 'COLLECTING_TIME') {
    const time = parseTime(message)
    if (!time) {
      response = 'No reconocí la hora. Usa el formato HH:MM (ej. 10:00).'
    } else {
      const bh = await getBusinessHours()
      if (!isWithinBusinessHours(time, ctx.date, bh)) {
        response = `Ese horario está fuera de nuestro horario (${bh.open_time}–${bh.close_time}). ¿Prefieres otro horario?`
      } else if (await isTimeConflict(ctx.date, time)) {
        response = `Ese horario ya está ocupado. ¿Qué tal las ${nextHour(time)}?`
      } else {
        ctx.time = time
        const dateLabel = new Date(ctx.date + 'T12:00:00')
          .toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
        response = `Confirmando tu cita:\n👤 ${ctx.name} · 🐾 ${ctx.petName}\n✂️ ${ctx.serviceName}\n📅 ${dateLabel} · ⏰ ${time}\n\n¿Confirmas? (sí / no)`
        newState = 'CONFIRMING'
      }
    }
  }

  // ── CONFIRMING ────────────────────────────────────────────────────────────
  else if (state === 'CONFIRMING') {
    const lower = message.toLowerCase().trim()
    if (['sí', 'si', 's', 'yes', 'ok'].includes(lower)) {
      await bookAppointment({ ...ctx, phone })
      response = `¡Listo! 🎉 Tu cita está agendada.\nTe esperamos el ${ctx.date} a las ${ctx.time}.\n📍 Privada Granada, Mitras Poniente`
      await send(config.operatorPhone, `✅ Cita agendada: ${ctx.name} · ${ctx.petName} · ${ctx.serviceName} · ${ctx.date} ${ctx.time}`)
      newState = 'DONE'
      ctx      = {}
    } else if (['no', 'n'].includes(lower)) {
      response = '¿Quieres cambiar algo? Escribe "cita" para empezar de nuevo.'
      newState = 'IDLE'
      ctx      = {}
    } else {
      response = 'Responde *sí* para confirmar o *no* para cancelar.'
    }
  }

  await saveSession(phone, newState, ctx)

  if (response) {
    await send(phone, response)
    // Notificar a Melina al inicio de cada sesión nueva (solo una vez)
    if (state === 'IDLE' && newState === 'COLLECTING_NAME') {
      await send(config.operatorPhone, `🤖 Nueva conversación: ${phone} — "${message}"`)
    }
  }
}

module.exports = { handleMessage, parseDate, parseTime, isWithinBusinessHours }
```

- [ ] **Step 2: Correr todos los tests — deben seguir pasando**

```bash
cd washdog-bot
npm test
```

Expected: 32 tests passed (los helpers siguen funcionando ya que están re-exportados).

- [ ] **Step 3: Commit**

```bash
git add washdog-bot/src/flowEngine.js
git commit -m "feat: implement flowEngine state machine (IDLE→DONE with all states)"
```

---

### Task 7: operatorRouter.js

**Files:**
- Create: `washdog-bot/src/operatorRouter.js`

- [ ] **Step 1: Crear `washdog-bot/src/operatorRouter.js`**

```javascript
const supabase = require('./supabaseClient')
const { send } = require('./twilioSender')
const config   = require('./config')

function normalizePhone(input) {
  // Acepta "8112345678", "+528112345678", "whatsapp:+528112345678"
  const digits = input.replace(/\D/g, '')
  if (digits.length === 10) return `whatsapp:+52${digits}`
  if (digits.length === 12 && digits.startsWith('52')) return `whatsapp:+${digits}`
  return `whatsapp:+${digits}`
}

async function handle(message) {
  const text = (message || '').trim()

  // !ver — lista sesiones activas
  if (text === '!ver') {
    const { data } = await supabase
      .from('sessions')
      .select('phone, state, operator_mode')
      .neq('state', 'IDLE')
    if (!data || data.length === 0) {
      await send(config.operatorPhone, 'No hay sesiones activas.')
    } else {
      const lines = data.map(s =>
        `${s.phone} → ${s.state}${s.operator_mode ? ' [OPERADOR]' : ''}`
      )
      await send(config.operatorPhone, 'Sesiones activas:\n' + lines.join('\n'))
    }
    return
  }

  // !pausa <número>
  if (text.startsWith('!pausa ')) {
    const phone = normalizePhone(text.slice(7).trim())
    await supabase.from('sessions').upsert({
      phone,
      operator_mode: true,
      updated_at: new Date().toISOString(),
    })
    await send(config.operatorPhone,
      `✅ Modo operador activo para ${phone}.\nLos mensajes del cliente llegarán aquí.\nEscribe !bot ${text.slice(7).trim()} para devolver el control.`
    )
    await send(phone, 'En un momento te atiende alguien. 👋')
    return
  }

  // !bot <número>
  if (text.startsWith('!bot ')) {
    const phone = normalizePhone(text.slice(5).trim())
    await supabase.from('sessions').upsert({
      phone,
      operator_mode: false,
      state: 'IDLE',
      context: {},
      updated_at: new Date().toISOString(),
    })
    await send(config.operatorPhone, `✅ Bot reactivado para ${phone}.`)
    await send(phone, '¡Hola de nuevo! 🐾 ¿En qué puedo ayudarte?\n1️⃣ Agendar cita  2️⃣ Precios  3️⃣ Horario  4️⃣ Dónde estamos')
    return
  }

  // !cita nombre | mascota | servicio | yyyy-mm-dd | HH:mm | teléfono
  if (text.startsWith('!cita ')) {
    const parts = text.slice(6).split('|').map(s => s.trim())
    if (parts.length < 6) {
      await send(config.operatorPhone,
        '❌ Formato incorrecto.\n!cita nombre | mascota | servicio | 2026-05-15 | 10:00 | 8112345678'
      )
      return
    }
    const [name, petName, service, date, time, tel] = parts
    const cleanPhone = tel.replace(/\D/g, '')
    const phone      = normalizePhone(tel)

    // Upsert cliente
    const { data: existing } = await supabase.from('clients').select('id')
      .eq('phone', cleanPhone).maybeSingle()
    let clientId = existing?.id
    if (!clientId) {
      const { data: created } = await supabase.from('clients').insert({
        owner_name: name, pet_name: petName, phone: cleanPhone, last_visit: date,
      }).select('id').single()
      clientId = created?.id
    }

    await supabase.from('appointments').insert({
      client_id: clientId, client_name: name, pet_name: petName,
      service, date, time, status: 'SCHEDULED', service_location: 'HOME',
    })

    await send(phone,
      `✅ Tu cita fue agendada:\n✂️ ${service}\n📅 ${date} ⏰ ${time}\n📍 Privada Granada, Mitras Poniente`
    )
    await send(config.operatorPhone, `✅ Cita creada: ${name} · ${petName} · ${service} · ${date} ${time}`)
    return
  }

  // Comando desconocido
  await send(config.operatorPhone,
    '❓ Comandos disponibles:\n!ver\n!pausa <número>\n!bot <número>\n!cita nombre|mascota|servicio|fecha|hora|tel'
  )
}

module.exports = { handle }
```

- [ ] **Step 2: Correr todos los tests para verificar no se rompió nada**

```bash
cd washdog-bot
npm test
```

Expected: 32 tests passed.

- [ ] **Step 3: Commit**

```bash
git add washdog-bot/src/operatorRouter.js
git commit -m "feat: add operatorRouter (!ver, !pausa, !bot, !cita)"
```

---

### Task 8: index.js webhook endpoint

**Files:**
- Create: `washdog-bot/src/index.js`

- [ ] **Step 1: Crear `washdog-bot/src/index.js`**

```javascript
const express = require('express')
const config  = require('./config')
const { handleMessage } = require('./flowEngine')
const { handle: handleOperator } = require('./operatorRouter')

const app = express()
app.use(express.urlencoded({ extended: false }))
app.use(express.json())

app.get('/health', (_, res) => res.json({ status: 'ok' }))

app.post('/webhook', async (req, res) => {
  // Twilio envía campos en body: From, Body
  const from    = req.body.From    || req.body.from    || ''
  const message = (req.body.Body   || req.body.body    || '').trim()

  if (!from || !message) {
    return res.status(400).send('Bad Request')
  }

  try {
    if (from === config.operatorPhone) {
      await handleOperator(message)
    } else {
      await handleMessage(from, message)
    }
  } catch (err) {
    console.error('[webhook error]', err)
  }

  // Twilio espera 200 — respuesta vacía (mensajes se envían via REST, no TwiML)
  res.status(200).send('')
})

app.listen(config.port, () => {
  console.log(`Washdog Bot listening on port ${config.port}`)
})
```

- [ ] **Step 2: Verificar que el servidor arranca**

```bash
cd washdog-bot
npm run dev
```

Expected: `Washdog Bot listening on port 3000`

Ctrl+C para detener.

- [ ] **Step 3: Correr todos los tests**

```bash
npm test
```

Expected: 32 tests passed.

- [ ] **Step 4: Commit**

```bash
git add washdog-bot/src/index.js
git commit -m "feat: add Express webhook endpoint (/webhook + /health)"
```

---

## Fase C — Android

### Task 9: Dependencias OkHttp + BuildConfig + INTERNET permission

**Files:**
- Modify: `washdog-native/app/build.gradle.kts`
- Modify: `washdog-native/local.properties`
- Modify: `washdog-native/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Agregar INTERNET permission a `AndroidManifest.xml`**

Reemplazar el contenido completo por:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Washdog">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="unspecified"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 2: Agregar credenciales Supabase a `local.properties`**

Abrir `washdog-native/local.properties` y agregar al final (con los valores reales del Task 1):

```
supabase.url=https://xxxx.supabase.co
supabase.anonKey=eyJ...
```

- [ ] **Step 3: Actualizar `washdog-native/app/build.gradle.kts`**

Reemplazar el contenido completo por:

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}

android {
    namespace = "com.washdog.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.washdog.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "SUPABASE_URL",      "\"${localProps.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("supabase.anonKey", "")}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions {
        unitTests.all {
            it.jvmArgs("-Xmx512m")
            val cleanPath = (System.getenv("PATH") ?: "")
                .split(";")
                .filter { p -> !p.contains("\"") }
                .joinToString(";")
            it.environment("PATH", cleanPath)
        }
    }
    lint {
        disable += setOf(
            "OldTargetApi", "AndroidGradlePluginVersion", "GradleDependency",
            "DiscouragedApi", "UnusedResources", "MissingApplicationIcon"
        )
        abortOnError = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 4: Compilar para verificar**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd "C:\Users\PC\Desktop\Regalo mi novia\washdog-native"
.\gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add washdog-native/app/build.gradle.kts washdog-native/app/src/main/AndroidManifest.xml
git commit -m "feat(android): add OkHttp, BuildConfig Supabase fields and INTERNET permission"
```

---

### Task 10: SupabaseDataSource.kt

**Files:**
- Create: `washdog-native/app/src/main/java/com/washdog/app/data/SupabaseDataSource.kt`

- [ ] **Step 1: Crear `SupabaseDataSource.kt`**

```kotlin
package com.washdog.app.data

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class SupabaseDataSource(
    private val supabaseUrl: String,
    private val anonKey: String
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Snake_case Gson para Supabase (owner_name, pet_name, etc.)
    val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val JSON = "application/json".toMediaType()

    private fun baseHeaders() = okhttp3.Headers.Builder()
        .add("apikey", anonKey)
        .add("Authorization", "Bearer $anonKey")
        .add("Content-Type", "application/json")
        .build()

    suspend fun <T : Any> selectAll(table: String, type: Type): List<T> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table?select=*&order=id")
            .headers(baseHeaders())
            .get().build()
        val body = http.newCall(req).execute().use { it.body?.string() ?: "[]" }
        gson.fromJson<List<T>>(body, type) ?: emptyList()
    }

    suspend fun <T : Any> selectSingle(table: String, filter: String, type: Class<T>): T? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table?$filter&limit=1")
            .headers(baseHeaders())
            .get().build()
        val body = http.newCall(req).execute().use { it.body?.string() ?: "[]" }
        val list: List<T>? = gson.fromJson(body, TypeToken.getParameterized(List::class.java, type).type)
        list?.firstOrNull()
    }

    suspend fun upsertList(table: String, json: String) = withContext(Dispatchers.IO) {
        val headers = baseHeaders().newBuilder()
            .add("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table")
            .headers(headers)
            .post(json.toRequestBody(JSON)).build()
        http.newCall(req).execute().close()
    }

    suspend fun upsertSingle(table: String, json: String) = withContext(Dispatchers.IO) {
        val headers = baseHeaders().newBuilder()
            .add("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table")
            .headers(headers)
            .post(json.toRequestBody(JSON)).build()
        http.newCall(req).execute().close()
    }

    suspend fun delete(table: String, column: String, value: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$supabaseUrl/rest/v1/$table?$column=eq.$value")
            .headers(baseHeaders())
            .delete().build()
        http.newCall(req).execute().close()
    }
}
```

- [ ] **Step 2: Compilar**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd "C:\Users\PC\Desktop\Regalo mi novia\washdog-native"
.\gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/data/SupabaseDataSource.kt
git commit -m "feat(android): add SupabaseDataSource HTTP helper (OkHttp + Gson snake_case)"
```

---

### Task 11: AppRepository.kt — migrar de DataStore a Supabase

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/data/AppRepository.kt`

- [ ] **Step 1: Reemplazar `AppRepository.kt` completo**

```kotlin
package com.washdog.app.data

import android.content.Context
import com.google.gson.reflect.TypeToken
import com.washdog.app.BuildConfig
import com.washdog.app.data.models.Appointment
import com.washdog.app.data.models.BusinessHours
import com.washdog.app.data.models.Client
import com.washdog.app.data.models.Transaction
import com.washdog.app.data.models.ZonePrices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val db = SupabaseDataSource(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracking de IDs conocidos para detectar eliminaciones
    private var knownClientIds      = mutableSetOf<String>()
    private var knownAppointmentIds = mutableSetOf<String>()
    private var knownTransactionIds = mutableSetOf<String>()

    companion object {
        const val DEFAULT_WELCOME    = "Hola {{nombre}}, bienvenido/a. Estamos felices de tenerte con nosotros. 😊"
        const val DEFAULT_CONFIRM    = "Hola {{nombre}}, confirmamos tu cita para el {{fecha}} a las {{hora}}. ¡Te esperamos!"
        const val DEFAULT_RESCHEDULE = "Hola {{nombre}}, queremos coordinar una nueva fecha para tu cita. ¿Cuándo te queda bien?"
    }

    // ── HELPERS ───────────────────────────────────────────────────────────────

    private fun isValidUuid(s: String): Boolean =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(s)

    private fun ensureUuid(id: String): String =
        if (isValidUuid(id)) id else java.util.UUID.randomUUID().toString()

    // ── SAVES (fire-and-forget) ───────────────────────────────────────────────

    fun saveClients(list: List<Client>) {
        saveScope.launch {
            val safeList = list.map { it.copy(id = ensureUuid(it.id)) }
            val newIds = safeList.map { it.id }.toSet()
            // Eliminar clientes borrados
            (knownClientIds - newIds).forEach { id -> db.delete("clients", "id", id) }
            // Upsert restantes
            if (safeList.isNotEmpty()) db.upsertList("clients", db.gson.toJson(safeList))
            knownClientIds = newIds.toMutableSet()
        }
    }

    fun saveAppointments(list: List<Appointment>) {
        saveScope.launch {
            val safeList = list.map { it.copy(id = ensureUuid(it.id)) }
            val newIds = safeList.map { it.id }.toSet()
            (knownAppointmentIds - newIds).forEach { id -> db.delete("appointments", "id", id) }
            if (safeList.isNotEmpty()) db.upsertList("appointments", db.gson.toJson(safeList))
            knownAppointmentIds = newIds.toMutableSet()
        }
    }

    fun saveTransactions(list: List<Transaction>) {
        saveScope.launch {
            val safeList = list.map { it.copy(id = ensureUuid(it.id)) }
            val newIds = safeList.map { it.id }.toSet()
            (knownTransactionIds - newIds).forEach { id -> db.delete("transactions", "id", id) }
            if (safeList.isNotEmpty()) db.upsertList("transactions", db.gson.toJson(safeList))
            knownTransactionIds = newIds.toMutableSet()
        }
    }

    fun saveTemplates(welcome: String, confirm: String, reschedule: String) {
        saveScope.launch {
            val rows = listOf(
                mapOf("key" to "welcome",    "body" to welcome),
                mapOf("key" to "confirm",    "body" to confirm),
                mapOf("key" to "reschedule", "body" to reschedule),
            )
            db.upsertList("templates", db.gson.toJson(rows))
        }
    }

    fun saveBusinessHours(bh: BusinessHours) {
        saveScope.launch {
            // Supabase column names are snake_case; BusinessHours fields are camelCase
            // SupabaseDataSource.gson uses LOWER_CASE_WITH_UNDERSCORES — handles mapping
            val json = "[" + db.gson.toJson(mapOf(
                "id"          to 1,
                "open_time"   to bh.openTime,
                "close_time"  to bh.closeTime,
                "closed_days" to bh.closedDays,
            )) + "]"
            db.upsertList("business_hours", json)
        }
    }

    fun saveZonePrices(zp: ZonePrices) {
        saveScope.launch {
            val json = "[" + db.gson.toJson(mapOf(
                "id"            to 1,
                "near_fee"      to zp.nearFee,    "near_buffer"   to zp.nearBuffer,
                "close_fee"     to zp.closeFee,   "close_buffer"  to zp.closeBuffer,
                "medium_fee"    to zp.mediumFee,  "medium_buffer" to zp.mediumBuffer,
                "far_fee"       to zp.farFee,     "far_buffer"    to zp.farBuffer,
            )) + "]"
            db.upsertList("zone_prices", json)
        }
    }

    fun markInitialized() {
        context.getSharedPreferences("washdog_meta", Context.MODE_PRIVATE)
            .edit().putBoolean("initialized", true).apply()
    }

    suspend fun isInitialized(): Boolean = withContext(Dispatchers.IO) {
        context.getSharedPreferences("washdog_meta", Context.MODE_PRIVATE)
            .getBoolean("initialized", false)
    }

    // ── LOADS (suspend) ───────────────────────────────────────────────────────

    suspend fun loadClients(): List<Client> {
        return try {
            val list: List<Client> = db.selectAll("clients",
                TypeToken.getParameterized(List::class.java, Client::class.java).type)
            knownClientIds = list.map { it.id }.toMutableSet()
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadAppointments(): List<Appointment> {
        return try {
            val list: List<Appointment> = db.selectAll("appointments",
                TypeToken.getParameterized(List::class.java, Appointment::class.java).type)
            knownAppointmentIds = list.map { it.id }.toMutableSet()
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadTransactions(): List<Transaction> {
        return try {
            val list: List<Transaction> = db.selectAll("transactions",
                TypeToken.getParameterized(List::class.java, Transaction::class.java).type)
            knownTransactionIds = list.map { it.id }.toMutableSet()
            list
        } catch (e: Exception) { emptyList() }
    }

    suspend fun loadTemplates(): Triple<String, String, String> {
        return try {
            data class TplRow(val key: String, val body: String)
            val rows: List<TplRow> = db.selectAll("templates",
                TypeToken.getParameterized(List::class.java, TplRow::class.java).type)
            val map = rows.associateBy { it.key }
            Triple(
                map["welcome"]?.body    ?: DEFAULT_WELCOME,
                map["confirm"]?.body    ?: DEFAULT_CONFIRM,
                map["reschedule"]?.body ?: DEFAULT_RESCHEDULE,
            )
        } catch (e: Exception) {
            Triple(DEFAULT_WELCOME, DEFAULT_CONFIRM, DEFAULT_RESCHEDULE)
        }
    }

    suspend fun loadBusinessHours(): BusinessHours {
        return try {
            data class BhRow(val openTime: String, val closeTime: String, val closedDays: List<Int>)
            db.selectSingle("business_hours", "id=eq.1", BhRow::class.java)
                ?.let { BusinessHours(it.openTime, it.closeTime, it.closedDays) }
                ?: BusinessHours()
        } catch (e: Exception) { BusinessHours() }
    }

    suspend fun loadZonePrices(): ZonePrices {
        return try {
            data class ZpRow(
                val nearFee: Double, val nearBuffer: Int,
                val closeFee: Double, val closeBuffer: Int,
                val mediumFee: Double, val mediumBuffer: Int,
                val farFee: Double, val farBuffer: Int,
            )
            db.selectSingle("zone_prices", "id=eq.1", ZpRow::class.java)
                ?.let { ZonePrices(it.nearFee, it.nearBuffer, it.closeFee, it.closeBuffer,
                    it.mediumFee, it.mediumBuffer, it.farFee, it.farBuffer) }
                ?: ZonePrices()
        } catch (e: Exception) { ZonePrices() }
    }
}
```

- [ ] **Step 2: Compilar**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd "C:\Users\PC\Desktop\Regalo mi novia\washdog-native"
.\gradlew compileDebugKotlin
```

Si hay error de `copy()` en Client o Appointment (porque los campos `id` son `val` y usan default), verificar que los modelos permiten copy. No debe haber errores si los modelos son data classes.

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Correr todos los tests**

```powershell
.\gradlew test
```

Verificar los XMLs en `app/build/test-results/`:
```powershell
Get-ChildItem "app\build\test-results" -Recurse -Filter "*.xml" | ForEach-Object {
    $c = Get-Content $_.FullName -Raw
    if ($c -match 'failures="(\d+)"') { "$($_.Name): failures=$($Matches[1])" }
}
```

Expected: `failures=0` en todos los archivos.

- [ ] **Step 4: Commit**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/data/AppRepository.kt
git commit -m "feat(android): migrate AppRepository from DataStore to Supabase REST"
```

---

### Task 12: Migración one-shot DataStore → Supabase

**Files:**
- Modify: `washdog-native/app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt`

La migración se ejecuta la primera vez que la app corre con el nuevo código: lee los datos del DataStore local y los sube a Supabase. El flag `initialized` ahora vive en SharedPreferences (en lugar de DataStore).

- [ ] **Step 1: Agregar método de migración en AppViewModel.kt**

Leer el AppViewModel actual y localizar el método `private suspend fun loadData()`.

Agregar un método privado `migrateFromDataStore()` justo antes de `loadData()`:

```kotlin
private suspend fun migrateFromDataStore() {
    // Solo si hay datos en DataStore y Supabase está vacío (primera ejecución post-migración)
    val prefs = androidx.datastore.preferences.core.PreferenceDataStoreFactory
        .create(produceFile = { context.applicationContext.getDatabasePath("washdog_prefs.preferences_pb") })
        .data.first()
    
    // Si no hay datos en DataStore, no hay nada que migrar
    val rawClients = prefs[androidx.datastore.preferences.core.stringPreferencesKey("clients")] ?: return
    if (rawClients.isBlank()) return
    
    // Si Supabase ya tiene datos, no migrar de nuevo
    val existingClients = repo.loadClients()
    if (existingClients.isNotEmpty()) {
        repo.markInitialized()
        return
    }

    val gson = com.google.gson.Gson()
    val clientType = object : com.google.gson.reflect.TypeToken<List<com.washdog.app.data.models.Client>>() {}.type
    val apptType   = object : com.google.gson.reflect.TypeToken<List<com.washdog.app.data.models.Appointment>>() {}.type
    val txType     = object : com.google.gson.reflect.TypeToken<List<com.washdog.app.data.models.Transaction>>() {}.type

    val oldClients: List<com.washdog.app.data.models.Client> = try {
        gson.fromJson(rawClients, clientType)
    } catch (e: Exception) { emptyList() }

    val rawAppt = prefs[androidx.datastore.preferences.core.stringPreferencesKey("appointments")] ?: ""
    val oldAppts: List<com.washdog.app.data.models.Appointment> = try {
        gson.fromJson(rawAppt, apptType)
    } catch (e: Exception) { emptyList() }

    val rawTx = prefs[androidx.datastore.preferences.core.stringPreferencesKey("transactions")] ?: ""
    val oldTx: List<com.washdog.app.data.models.Transaction> = try {
        gson.fromJson(rawTx, txType)
    } catch (e: Exception) { emptyList() }

    if (oldClients.isNotEmpty()) repo.saveClients(oldClients)
    if (oldAppts.isNotEmpty()) repo.saveAppointments(oldAppts)
    if (oldTx.isNotEmpty()) repo.saveTransactions(oldTx)

    repo.markInitialized()
}
```

- [ ] **Step 2: Llamar a migrateFromDataStore() en loadData()**

Localizar `private suspend fun loadData()` y agregar como primera línea del cuerpo:

```kotlin
private suspend fun loadData() {
    if (!repo.isInitialized()) migrateFromDataStore()
    // ... resto del método sin cambio
```

- [ ] **Step 3: Compilar**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
cd "C:\Users\PC\Desktop\Regalo mi novia\washdog-native"
.\gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Build APK final**

```powershell
.\gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` y APK en `app/build/outputs/apk/debug/`.

- [ ] **Step 5: Commit final**

```bash
git add washdog-native/app/src/main/java/com/washdog/app/viewmodel/AppViewModel.kt
git commit -m "feat(android): add one-shot DataStore→Supabase migration on first launch"
```

---

## Verificación Post-Implementación

| Escenario | Pasos | Resultado esperado |
|-----------|-------|-------------------|
| Bot: saludo | Cliente escribe "hola" | Bot muestra menú de 4 opciones |
| Bot: flujo completo | Cliente sigue el flujo hasta confirmar | Cita aparece en Supabase y en la app Android |
| Bot: conflicto de horario | Pedir misma hora que cita existente | Bot sugiere siguiente hora |
| Bot: fuera de horario | Hora 22:00 | Bot pide otro horario |
| Melina: !ver | Melina escribe "!ver" | Lista de sesiones activas |
| Melina: !pausa | Melina escribe "!pausa NÚMERO" | Bot silencioso, reenvía mensajes a Melina |
| Melina: !cita | Melina crea cita directamente | Cita en Supabase, cliente recibe confirmación |
| Android: primera apertura con datos viejos | Instalar APK con DataStore existente | Datos migrados a Supabase |
| Android: guardar cliente | Crear cliente en app | Cliente visible en Supabase dashboard |
| Android: cita bot aparece en app | Bot agenda cita | Visible en Agenda de la app |
