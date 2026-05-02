const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? '/api'
  : 'http://localhost:8123/api'

/**
 * 获取对话列表
 */
export const getConversations = async () => {
  const res = await fetch(`${API_BASE_URL}/chat/conversations`)
  return res.json()
}

/**
 * 获取对话消息
 */
export const getMessages = async (convId) => {
  const res = await fetch(`${API_BASE_URL}/chat/conversations/${convId}/messages`)
  return res.json()
}

/**
 * 创建新对话
 */
export const createConversation = async () => {
  const res = await fetch(`${API_BASE_URL}/chat/conversations`, { method: 'POST' })
  return res.json()
}

/**
 * 删除对话
 */
export const deleteConversation = async (convId) => {
  const res = await fetch(`${API_BASE_URL}/chat/conversations/${convId}`, { method: 'DELETE' })
  return res.json()
}

/**
 * 普通聊天（POST）
 */
export const chat = async (message, chatId, agentMode = false) => {
  const res = await fetch(`${API_BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, chatId, agentMode }),
  })
  return res.json()
}

/**
 * SSE 流式聊天
 */
export const chatSSE = (message, convId) => {
  const params = new URLSearchParams({ message, convId })
  const url = `${API_BASE_URL}/chat/sse?${params}`
  return new EventSource(url)
}

export default {
  getConversations, getMessages, createConversation,
  deleteConversation, chat, chatSSE,
}
