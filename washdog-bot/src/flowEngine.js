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
