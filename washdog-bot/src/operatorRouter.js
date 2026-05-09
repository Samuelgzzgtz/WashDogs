const supabase = require('./supabaseClient')
const { send } = require('./twilioSender')
const config = require('./config')

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
    const phone = normalizePhone(tel)

    // Upsert cliente
    const { data: existing } = await supabase.from('clients').select('id')
      .eq('phone', cleanPhone).maybeSingle()
    let clientId = existing?.id
    if (!clientId) {
      const { data: created } = await supabase.from('clients').insert({
        owner_name: name, pet_name: petName, phone: cleanPhone, last_visit: date,
      }).select('id').single()
      clientId = created?.id
      if (!clientId) {
        await send(config.operatorPhone, '❌ Error al crear el cliente. Intenta de nuevo.')
        return
      }
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
