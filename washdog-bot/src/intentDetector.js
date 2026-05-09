// Orden importa: reglas más específicas (multi-palabra) antes que las genéricas.
// STATUS antes que BOOK: "mi cita" / "tengo cita" contienen "cita" (keyword de BOOK).
// GREETING antes que HOURS: "buenos días" contiene "días" (keyword de HOURS).
// BOOK antes que GREETING: "hola quiero cita" debe → BOOK, no GREETING.
const RULES = [
  { intent: 'STATUS',   keywords: ['mi cita', 'confirmada', 'tengo cita'] },
  { intent: 'BOOK',     keywords: ['cita', 'agendar', 'reservar', 'quiero'] },
  { intent: 'PRICE',    keywords: ['precio', 'costo', 'cuánto', 'cuanto', 'tarifa'] },
  { intent: 'GREETING', keywords: ['hola', 'buenas', 'hey', 'buenos días', 'buenos dias', 'buenas tardes', 'buenas noches'] },
  { intent: 'HOURS',    keywords: ['horario', 'horas', 'días', 'dias', 'abren'] },
  { intent: 'LOCATION', keywords: ['dónde', 'donde', 'dirección', 'direccion', 'ubicación', 'ubicacion', 'cómo llegar', 'como llegar'] },
]

function detectIntent(message) {
  const lower = message.toLowerCase()
  for (const { intent, keywords } of RULES) {
    if (keywords.some(kw => lower.includes(kw))) return intent
  }
  return 'UNKNOWN'
}

module.exports = { detectIntent }
