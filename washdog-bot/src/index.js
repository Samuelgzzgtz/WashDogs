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
