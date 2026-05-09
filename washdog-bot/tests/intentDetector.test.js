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
