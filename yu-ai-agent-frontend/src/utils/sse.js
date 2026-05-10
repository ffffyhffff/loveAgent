/**
 * 从 fetch Response 的 ReadableStream 读取 SSE 数据
 * 因为 EventSource 只支持 GET，POST SSE 需要用 fetch + ReadableStream
 *
 * @param {Response} response - fetch 返回的 Response 对象
 * @param {Object} callbacks - { onEvent(parsed), onDone(), onError(err) }
 */
export async function consumeSSE(response, { onEvent, onDone, onError }) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split(/\r?\n\r?\n/)
      buffer = parts.pop() // 保留不完整的 chunk

      for (const part of parts) {
        if (!part.trim()) continue
        const dataLines = part
          .split(/\r?\n/)
          .filter(l => l.startsWith('data:'))
          .map(l => l.slice(5).trim())
        if (dataLines.length === 0) continue

        const jsonStr = dataLines.join('\n')
        try {
          const parsed = JSON.parse(jsonStr)
          if (parsed.type === 'done') {
            onDone()
            return
          }
          if (parsed.type === 'error') {
            onError(parsed)
            return
          }
          onEvent(parsed)
        } catch {
          // 非 JSON 数据行，跳过
        }
      }
    }
    onDone()
  } catch (e) {
    if (e.name === 'AbortError') return
    onError({ type: 'error', message: e.message })
  }
}
