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
