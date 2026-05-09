const twilio = require('twilio')
const config = require('./config')

const client = twilio(config.twilio.accountSid, config.twilio.authToken)

async function send(to, body) {
  return client.messages.create({ from: config.twilio.from, to, body })
}

module.exports = { send }
