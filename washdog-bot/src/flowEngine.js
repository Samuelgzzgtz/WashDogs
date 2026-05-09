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

async function getSession(supabase, phone) {
  const { data } = await supabase.from('sessions').select('*').eq('phone', phone).single()
  return data ?? { phone, state: 'IDLE', context: {}, operator_mode: false }
}

async function saveSession(supabase, phone, state, context) {
  await supabase.from('sessions').upsert({ phone, state, context, updated_at: new Date().toISOString() })
}

async function getBusinessHours(supabase) {
  const { data } = await supabase.from('business_hours').select('*').eq('id', 1).single()
  return data ?? { open_time: '09:00', close_time: '19:00', closed_days: [0] }
}

async function isTimeConflict(supabase, date, time) {
  const { data } = await supabase.from('appointments').select('time').eq('date', date)
  return (data ?? []).some(a => a.time === time)
}

async function bookAppointment(supabase, ctx) {
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
    if (!clientId) throw new Error('Failed to create client record')
  } else {
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
  // Lazy requires — only resolved when handleMessage is actually called,
  // so requiring flowEngine.js in tests (without .env) does NOT throw.
  const supabase           = require('./supabaseClient')
  const { send }           = require('./twilioSender')
  const config             = require('./config')
  const { detectIntent }   = require('./intentDetector')

  const session = await getSession(supabase, phone)

  if (session.operator_mode) {
    // Bot silencioso; reenviar mensaje a Melina.
    // Operator mode is set/cleared exclusively by operatorRouter — no state mutation here.
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
    else if (lower === '2') { response = `Nuestros servicios:\n${serviceMenu()}` }
    else if (lower === '3') {
      const bh = await getBusinessHours(supabase)
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
      const bh = await getBusinessHours(supabase)
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
        await saveSession(supabase, phone, 'IDLE', { ...ctx, needsOperator: true })
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
      const bh = await getBusinessHours(supabase)
      if (!isWithinBusinessHours(time, ctx.date, bh)) {
        response = `Ese horario está fuera de nuestro horario (${bh.open_time}–${bh.close_time}). ¿Prefieres otro horario?`
      } else if (await isTimeConflict(supabase, ctx.date, time)) {
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
      try {
        await bookAppointment(supabase, { ...ctx, phone })
      } catch (err) {
        console.error('[bookAppointment error]', err)
        response = 'Hubo un error al agendar tu cita. Por favor intenta de nuevo escribiendo "sí".'
        await saveSession(supabase, phone, 'CONFIRMING', ctx)
        await send(phone, response)
        return
      }
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

  await saveSession(supabase, phone, newState, ctx)

  if (response) {
    await send(phone, response)
    // Notificar a Melina al inicio de cada sesión nueva (solo una vez)
    if (state === 'IDLE' && newState === 'COLLECTING_NAME') {
      await send(config.operatorPhone, `🤖 Nueva conversación: ${phone} — "${message}"`)
    }
  }
}

module.exports = { handleMessage, parseDate, parseTime, isWithinBusinessHours }
