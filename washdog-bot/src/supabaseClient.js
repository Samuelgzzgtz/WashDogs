const { createClient } = require('@supabase/supabase-js')
const config = require('./config')

module.exports = createClient(config.supabase.url, config.supabase.serviceKey)
