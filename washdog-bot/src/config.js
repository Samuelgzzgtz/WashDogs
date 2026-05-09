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
