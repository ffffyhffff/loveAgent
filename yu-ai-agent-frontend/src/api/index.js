const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? '/api'
  : 'http://localhost:8123/api'

export const getConversations = async () => {
  const res = await fetch(`${API_BASE_URL}/chat/conversations`)
  return res.json()
}

export const getMessages = async (convId) => {
  const res = await fetch(`${API_BASE_URL}/chat/conversations/${convId}/messages`)
  return res.json()
}

export const createConversation = async () => {
  const res = await fetch(`${API_BASE_URL}/chat/conversations`, { method: 'POST' })
  return res.json()
}

export const deleteConversation = async (convId) => {
  const res = await fetch(`${API_BASE_URL}/chat/conversations/${convId}`, { method: 'DELETE' })
  return res.json()
}

export const chat = async (message, chatId) => {
  const res = await fetch(`${API_BASE_URL}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, chatId }),
  })
  return res.json()
}

export const chatSSE = (message, convId) => {
  const params = new URLSearchParams({ message, convId })
  return new EventSource(`${API_BASE_URL}/chat/sse?${params}`)
}

/**
 * Step 1: 分析用户意图（只提取信息，不生成计划）
 */
export const datePlanAnalyze = async (message) => {
  const res = await fetch(`${API_BASE_URL}/chat/date-plan/analyze`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })
  return res.json()
}

/**
 * Step 1b: 用户补充偏好后生成计划
 */
export const datePlanGenerate = async (prefs) => {
  const res = await fetch(`${API_BASE_URL}/chat/date-plan/plan`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(prefs),
  })
  return res.json()
}

/**
 * Step 2: 执行 Agent 计划（SSE 流式）
 */
export const datePlanExecute = (location, budget, style, convId, occasion = '', activity = '') => {
  const params = new URLSearchParams({ location, budget, style, convId, occasion, activity })
  return new EventSource(`${API_BASE_URL}/chat/date-plan/execute?${params}`)
}

/**
 * 意图路由：判断消息是 chat 还是 plan
 */
export const routeIntent = async (message) => {
  const res = await fetch(`${API_BASE_URL}/chat/route-intent`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  })
  return res.json()
}

/**
 * LoveAgent 流式对话（初始消息）
 * 返回 { promise, abort } — promise 解析为 fetch Response，需要用 consumeSSE 读取
 */
export const loveStreamInit = (message, convId) => {
  const controller = new AbortController()
  const promise = fetch(`${API_BASE_URL}/chat/love-stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, convId }),
    signal: controller.signal,
  })
  return { promise, abort: () => controller.abort() }
}

/**
 * LoveAgent 流式对话（表单提交后续聊）
 */
export const loveStreamResume = (convId, formId, answers) => {
  const controller = new AbortController()
  const promise = fetch(`${API_BASE_URL}/chat/love-stream/resume`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ convId, formId, answers }),
    signal: controller.signal,
  })
  return { promise, abort: () => controller.abort() }
}

/**
 * 重新生成路线+PDF（可视化选择后）
 */
export const regeneratePlan = (data) => {
  const controller = new AbortController()
  const promise = fetch(`${API_BASE_URL}/chat/regenerate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data),
    signal: controller.signal,
  })
  return { promise, abort: () => controller.abort() }
}

/**
 * AI 对话式修改约会计划（SSE 流）
 */
export const modifyPlanStream = (message, location) => {
  const controller = new AbortController()
  const promise = fetch(`${API_BASE_URL}/chat/modify-stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, location }),
    signal: controller.signal,
  })
  return { promise, abort: () => controller.abort() }
}

export default {
  getConversations, getMessages, createConversation,
  deleteConversation, chat, chatSSE,
  datePlanAnalyze, datePlanGenerate, datePlanExecute,
  routeIntent, loveStreamInit, loveStreamResume,
  regeneratePlan, modifyPlanStream,
}
