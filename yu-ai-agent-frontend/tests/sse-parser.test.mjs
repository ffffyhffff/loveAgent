import assert from 'node:assert/strict'
import { consumeSSE } from '../src/utils/sse.js'

async function consumeRawSse(raw) {
  const encoder = new TextEncoder()
  const response = {
    body: new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(raw))
        controller.close()
      },
    }),
  }

  const events = []
  let done = false
  await consumeSSE(response, {
    onEvent(event) {
      events.push(event)
    },
    onDone() {
      done = true
    },
    onError(error) {
      throw new Error(error.message || 'SSE parse failed')
    },
  })

  return { events, done }
}

const { events, done } = await consumeRawSse(
  'data: {"type":"step","status":"active","index":0}\r\n\r\n'
)

assert.equal(done, true)
assert.equal(events.length, 1)
assert.deepEqual(events[0], { type: 'step', status: 'active', index: 0 })
