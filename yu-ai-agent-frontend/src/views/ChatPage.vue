<template>
  <div class="chat-page">
    <!-- 侧边栏遮罩 -->
    <div class="sidebar-overlay" :class="{ show: sidebarOpen }" @click="sidebarOpen = false"></div>
    <Sidebar
      :conversations="conversations"
      :activeId="activeId"
      :sidebarOpen="sidebarOpen"
      @toggle="sidebarOpen = !sidebarOpen"
      @new="newConversation"
      @switch="switchConversation"
      @delete="deleteConv"
    />

    <!-- 主区域 -->
    <div class="chat-main" :class="{ 'agent-mode': mode === 'agent' }">
      <!-- ChatPanel (Task 3 抽出) -->
      <ChatPanel
        v-model="text"
        :messages="chatMessages"
        :connecting="connecting"
        @send="send"
        @toggleSidebar="sidebarOpen = !sidebarOpen"
        @changePoi="findPoisMsgAndOpen"
        @aiModify="showModifyInput = true"
      />

      <ResultPanel
        v-if="mode === 'agent'"
        :activeTab="resultTab"
        :steps="planSteps"
        :toolCalls="toolCalls"
        :pois="resultPois"
        :selectedPois="resultSelectedPois"
        :routeInfo="resultRouteInfo"
        :pdfUrl="resultPdfUrl"
        :streaming="connecting"
        @update:activeTab="resultTab = $event"
        @close="mode = 'chat'"
      />
    </div>

    <!-- LoveAgent 动态表单弹窗 -->
    <DynamicForm v-if="activeFormSpec"
      :formSpec="activeFormSpec"
      @submit="handleFormSubmit"
      @close="activeFormSpec = null"
    />

    <!-- POI 选择器弹窗 -->
    <Teleport to="body">
      <div v-if="poiSelectorVisible" class="dialog-overlay" @click.self="poiSelectorVisible = false">
        <PoiSelector
          :categories="poiSelectorData.categories"
          :selected="poiSelectorData.selected"
          @confirm="handlePoiConfirm"
          @close="poiSelectorVisible = false"
        />
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, provide } from 'vue'
import {
  chatSSE, getConversations, getMessages, createConversation, deleteConversation,
  routeIntent, loveStreamInit, loveStreamResume, regeneratePlan, modifyPlanStream,
} from '../api'
import { consumeSSE } from '../utils/sse'
import Sidebar from '../components/Sidebar.vue'
import ChatPanel from '../components/ChatPanel.vue'
import ResultPanel from '../components/ResultPanel.vue'
import DynamicForm from '../components/DynamicForm.vue'
import PoiSelector from '../components/PoiSelector.vue'

// ==================== 会话 ====================
const conversations = ref([])
const activeId = ref('')
const sidebarOpen = ref(false)

// ==================== 聊天 ====================
const chatMessages = ref([])
const text = ref('')
const connecting = ref(false)

// ==================== Agent 模式 ====================
const mode = ref('chat')           // 'chat' | 'agent'
const planSteps = ref([])          // [{label, status, detail}]
const toolCalls = ref([])          // [{toolName, toolInput, status, results[], startTime}]
const resultTab = ref('plan')      // 'plan' | 'tools' | 'map' | 'pdf'
const resultPois = ref([])
const resultSelectedPois = ref([])
const resultRouteInfo = ref(null)
const resultPdfUrl = ref(null)

// ==================== LoveAgent ====================
const activeFormSpec = ref(null)
let loveAbort = null
let pendingFormCleanup = null

// ==================== POI 修改 ====================
const poiSelectorVisible = ref(false)
const poiSelectorData = reactive({ categories: [], selected: {} })
const modifyLocation = ref('')

// provide 给子组件
provide('agentState', {
  mode, planSteps, toolCalls, resultTab, connecting,
  resultPois, resultSelectedPois, resultRouteInfo, resultPdfUrl,
})

// ==================== SSE 事件处理 ====================

const applyLoveEvent = (parsed, target, state) => {
  // text → chatMessages
  if (parsed.type === 'text' && parsed.content) {
    // 如果已经有执行面板了，文字结论放在新气泡
    if (state._execMsg || mode.value === 'agent') {
      target.push({ content: parsed.content, isUser: false })
    } else if (state.aiIndex != null && target[state.aiIndex]) {
      target[state.aiIndex].content += parsed.content
    } else if (state.aiIndex == null) {
      target.push({ content: parsed.content, isUser: false })
    }
  }

  // plan → mode switch, planSteps
  if (parsed.type === 'plan' && parsed.steps) {
    mode.value = 'agent'
    planSteps.value = parsed.steps.map((s, i) => ({
      label: s.message || s.text || `步骤 ${i + 1}`,
      status: s.status || 'pending',
      detail: s.detail || '',
    }))
    // 也显示执行面板在聊天流中
    const execMsg = { content: '', isUser: false, type: 'execution',
      execution: { steps: parsed.steps.map((s, i) => ({
        text: s.message || s.text || `步骤 ${i + 1}`,
        status: s.status || 'pending',
      })), results: [], details: [] } }
    target.push(execMsg)
    state._execMsg = execMsg
  }

  // step → update planSteps + track tool calls
  if (parsed.type === 'step') {
    const idx = parsed.index
    if (idx != null && idx < planSteps.value.length) {
      planSteps.value[idx].status = parsed.status || planSteps.value[idx].status
      planSteps.value[idx].label = parsed.message || planSteps.value[idx].label
    }
    // step 变为 active 时创建工具调用记录
    if (parsed.status === 'active' && parsed.message) {
      const tc = toolCalls.value.find(tc => tc.stepIndex === idx && tc.status === 'running')
      if (!tc) {
        toolCalls.value.push({
          stepIndex: idx,
          toolName: extractToolName(parsed.message),
          toolInput: parsed.message,
          status: 'running',
          results: [],
          startTime: Date.now(),
        })
      }
    }
    // step 完成时标记工具调用结束，记录真实耗时
    if (parsed.status === 'done') {
      const runningTc = toolCalls.value.find(tc => tc.stepIndex === idx && (tc.status === 'running' || tc.status === 'results_ready'))
      if (runningTc) {
        runningTc.status = 'done'
        runningTc.duration = parsed.duration || null
      }
    }
    // Update exec msg steps if present
    if (state._execMsg) {
      const steps = state._execMsg.execution.steps
      if (idx != null && idx < steps.length) {
        steps[idx].status = parsed.status || steps[idx].status
        steps[idx].text = parsed.message || steps[idx].text
      }
    }
  }

  // section → resultPois + toolCalls (只加结果，不改状态)
  if (parsed.type === 'section' && parsed.items) {
    resultPois.value = [...resultPois.value, ...parsed.items]
    resultTab.value = 'tools'
    const activeTc = toolCalls.value.find(tc => tc.status === 'running')
    if (activeTc) {
      activeTc.results = [...activeTc.results, ...parsed.items]
      // 不设 done，留给 step done 事件带 duration 来完成
    } else {
      toolCalls.value.push({
        toolName: parsed.title || 'POI搜索',
        toolInput: parsed.title || '',
        status: 'results_ready',
        results: [...parsed.items],
      })
    }
    if (state._execMsg) {
      state._execMsg.execution.results.push({
        icon: parsed.icon || getCatIcon(parsed.title),
        label: parsed.title,
        items: parsed.items.slice(0, 3),
      })
    }
  }

  // review → details
  if (parsed.type === 'review') {
    if (state._execMsg) {
      state._execMsg.execution.details.push({
        name: parsed.placeName || '',
        text: parsed.content || '',
        images: parsed.images || [],
      })
    }
  }

  // placeDetail → chat message
  if (parsed.type === 'placeDetail') {
    if (state._execMsg) {
      state._execMsg.execution.details.push({
        name: parsed.placeName || '',
        text: [parsed.address, parsed.rating ? `评分 ${parsed.rating}` : '', parsed.cost ? `人均 ${parsed.cost}` : ''].filter(Boolean).join(' '),
        images: parsed.images || [],
      })
    }
    target.push({
      content: '', isUser: false, type: 'placeDetail',
      keyword: parsed.keyword, placeName: parsed.placeName,
      address: parsed.address, rating: parsed.rating,
      cost: parsed.cost, openTime: parsed.openTime,
      images: parsed.images || [],
    })
  }

  // pois → resultPois + chat msg
  if (parsed.type === 'pois' && parsed.categories) {
    resultPois.value = parsed.categories.flatMap(c => c.items || [])
    target.push({
      content: '', isUser: false, type: 'pois',
      categories: parsed.categories, selected: parsed.selected || {},
    })
  }

  // map → resultSelectedPois + resultRouteInfo
  if (parsed.type === 'map') {
    resultSelectedPois.value = parsed.pois || []
    resultRouteInfo.value = parsed.routeInfo || null
    if (parsed.location) modifyLocation.value = parsed.location
    resultTab.value = 'map'
    target.push({
      content: '', isUser: false, type: 'map',
      pois: parsed.pois || [], routeInfo: parsed.routeInfo || null,
    })
  }

  // pdf → resultPdfUrl
  if (parsed.type === 'pdf' && parsed.url) {
    resultPdfUrl.value = parsed.url
    resultTab.value = 'pdf'
    target.push({ content: '', isUser: false, type: 'pdf', url: parsed.url })
  }
}

const appendLoveEventsToMessages = (events, target, aiIndex = null) => {
  const state = { aiIndex, _execMsg: null }
  for (const evt of events) {
    applyLoveEvent(evt, target, state)
  }
  return state
}

// ==================== createLoveEventHandler ====================

const createLoveEventHandler = (aiIndex) => {
  let formTimer = null
  const state = { aiIndex, _execMsg: null }

  const handler = {
    onEvent(parsed) {
      if (parsed.type === 'form' && parsed.formSpec) {
        let spec = parsed.formSpec
        if (typeof spec === 'string') {
          try { spec = JSON.parse(spec) } catch { return }
        }
        if (!spec.fields || spec.fields.length === 0) {
          activeFormSpec.value = null
          return
        }
        chatMessages.value.push({ content: '', isUser: false, type: 'form', formSpec: spec })
        if (formTimer) clearTimeout(formTimer)
        formTimer = setTimeout(() => {
          activeFormSpec.value = spec
          formTimer = null
        }, 2000)
      } else if (parsed.type !== 'form') {
        applyLoveEvent(parsed, chatMessages.value, state)
      }
    },
    cleanup() {
      if (formTimer) { clearTimeout(formTimer); formTimer = null }
    },
  }
  return handler
}

// ==================== 会话管理 ====================

onMounted(async () => { await loadConversations() })

const loadConversations = async () => {
  try {
    conversations.value = await getConversations()
    if (conversations.value.length > 0) {
      await switchConversation(conversations.value[0].id)
    } else {
      await newConversation()
    }
  } catch (e) { console.error('加载对话列表失败', e) }
}

const loadMessages = async (convId) => {
  try {
    const data = await getMessages(convId)
    const loaded = []
    for (const m of data) {
      if (m.type === 'sse_events') {
        try {
          const events = JSON.parse(m.content)
          appendLoveEventsToMessages(events, loaded)
        } catch { /* malformed, skip */ }
      } else {
        loaded.push({ content: m.content, isUser: m.isUser === 'true' })
      }
    }
    chatMessages.value = loaded
  } catch (e) { console.error('加载消息失败', e) }
}

const switchConversation = async (id) => {
  if (pendingFormCleanup) { pendingFormCleanup(); pendingFormCleanup = null }
  activeFormSpec.value = null
  activeId.value = id
  sidebarOpen.value = false
  // 重置 Agent 状态
  mode.value = 'chat'
  planSteps.value = []
  toolCalls.value = []
  resultPois.value = []
  resultSelectedPois.value = []
  resultRouteInfo.value = null
  resultPdfUrl.value = null
  await loadMessages(id)
}

const newConversation = async () => {
  try {
    if (pendingFormCleanup) { pendingFormCleanup(); pendingFormCleanup = null }
    activeFormSpec.value = null
    const { id } = await createConversation()
    conversations.value = await getConversations()
    activeId.value = id
    chatMessages.value = []
    mode.value = 'chat'
    planSteps.value = []
    resultPois.value = []
    resultSelectedPois.value = []
    resultRouteInfo.value = null
    resultPdfUrl.value = null
    sidebarOpen.value = false
  } catch (e) { console.error('创建对话失败', e) }
}

const deleteConv = async (id) => {
  try {
    await deleteConversation(id)
    conversations.value = await getConversations()
    if (activeId.value === id) {
      if (conversations.value.length > 0) {
        await switchConversation(conversations.value[0].id)
      } else {
        await newConversation()
      }
    }
  } catch (e) { console.error('删除对话失败', e) }
}

// ==================== 发送逻辑 ====================

const send = async () => {
  if (!text.value.trim() || connecting.value) return
  const msg = text.value.trim()
  text.value = ''
  chatMessages.value.push({ content: msg, isUser: true })

  // Agent 面板已激活 → 后续消息走修改流程
  if (mode.value === 'agent') {
    await handleModifyStream(msg)
    return
  }

  try {
    const { intent } = await routeIntent(msg)
    if (intent === 'plan') {
      await handleLoveStream(msg)
    } else {
      handleNormalChat(msg)
    }
  } catch (e) {
    console.error('意图路由失败，走普通对话', e)
    handleNormalChat(msg)
  }
}

// ========== Agent 模式下的自然语言修改 ==========
const handleModifyStream = async (msg) => {
  connecting.value = true
  const currentPois = [...resultSelectedPois.value]
  // 重置 Agent 状态准备接收新结果
  planSteps.value = []
  toolCalls.value = []
  resultPois.value = []
  resultSelectedPois.value = []
  resultRouteInfo.value = null
  resultPdfUrl.value = null
  resultTab.value = 'plan'

  const aiIndex = chatMessages.value.length
  chatMessages.value.push({ content: '', isUser: false })

  try {
    const { promise } = modifyPlanStream(msg, modifyLocation.value, currentPois, activeId.value)
    const response = await promise
    if (!response.ok) {
      chatMessages.value[aiIndex].content = '修改请求失败'
      connecting.value = false
      return
    }
    const handler = createLoveEventHandler(aiIndex)
    pendingFormCleanup = handler.cleanup
    await consumeSSE(response, {
      ...handler,
      onDone() { connecting.value = false; loveAbort = null; getConversations().then(list => conversations.value = list) },
      onError(err) { chatMessages.value[aiIndex].content = err.message || '修改出错'; connecting.value = false; loveAbort = null },
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      chatMessages.value[aiIndex].content = '修改出错: ' + e.message
    }
    connecting.value = false
    loveAbort = null
  }
}

// ========== 普通对话 ==========
const handleNormalChat = (msg) => {
  const aiIndex = chatMessages.value.length
  chatMessages.value.push({ content: '', isUser: false })
  connecting.value = true

  const es = chatSSE(msg, activeId.value)
  es.onmessage = (event) => {
    let parsed
    try { parsed = JSON.parse(event.data) } catch { parsed = { type: 'text', content: event.data } }

    if (parsed.type === 'done' || parsed.type === '[DONE]') {
      connecting.value = false
      es.close()
      getConversations().then(list => conversations.value = list)
      return
    }
    if (parsed.type === 'text' && parsed.content && aiIndex < chatMessages.value.length) {
      chatMessages.value[aiIndex].content += parsed.content
    }
    if (parsed.type === 'error' && parsed.message) {
      if (aiIndex < chatMessages.value.length) chatMessages.value[aiIndex].content = parsed.message
      connecting.value = false
      es.close()
    }
  }
  es.onerror = () => {
    if (chatMessages.value[aiIndex] && !chatMessages.value[aiIndex].content) {
      chatMessages.value[aiIndex].content = '连接出错了，请重试'
    }
    connecting.value = false
    es.close()
  }
}

// ========== LoveAgent 流式对话 ==========
const handleLoveStream = async (msg) => {
  connecting.value = true
  const aiIndex = chatMessages.value.length
  chatMessages.value.push({ content: '', isUser: false })

  const { promise, abort } = loveStreamInit(msg, activeId.value)
  loveAbort = abort

  try {
    const response = await promise
    if (!response.ok) {
      chatMessages.value[aiIndex].content = '请求失败，请重试'
      connecting.value = false
      return
    }
    const handler = createLoveEventHandler(aiIndex)
    pendingFormCleanup = handler.cleanup
    await consumeSSE(response, {
      ...handler,
      onDone() {
        connecting.value = false
        loveAbort = null
        getConversations().then(list => conversations.value = list)
      },
      onError(err) {
        chatMessages.value[aiIndex].content = err.message || '请求出错'
        connecting.value = false
        loveAbort = null
      },
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      console.error('LoveStream 失败', e)
      chatMessages.value[aiIndex].content = '请求出错: ' + e.message
    }
    connecting.value = false
    loveAbort = null
  }
}

// ========== LoveAgent 表单提交 ==========
const handleFormSubmit = ({ formId, answers }) => {
  activeFormSpec.value = null
  const parts = Object.entries(answers)
    .filter(([, v]) => v !== '' && v !== null && v !== undefined)
    .map(([k, v]) => `${k}：${Array.isArray(v) ? v.join(', ') : v}`)
  if (parts.length > 0) {
    chatMessages.value.push({ content: parts.join('，'), isUser: true })
  }

  connecting.value = true
  const aiIndex = chatMessages.value.length
  chatMessages.value.push({ content: '', isUser: false })

  const { promise, abort } = loveStreamResume(activeId.value, formId, answers)
  loveAbort = abort

  promise.then(response => {
    if (!response.ok) {
      chatMessages.value[aiIndex].content = '请求失败，请重试'
      connecting.value = false
      return
    }
    const handler = createLoveEventHandler(aiIndex)
    pendingFormCleanup = handler.cleanup
    return consumeSSE(response, {
      ...handler,
      onDone() {
        connecting.value = false
        loveAbort = null
        getConversations().then(list => conversations.value = list)
      },
      onError(err) {
        chatMessages.value[aiIndex].content = err.message || '请求出错'
        connecting.value = false
        loveAbort = null
      },
    })
  }).catch(e => {
    if (e.name !== 'AbortError') {
      console.error('LoveStream resume 失败', e)
      chatMessages.value[aiIndex].content = '请求出错: ' + e.message
    }
    connecting.value = false
    loveAbort = null
  })
}

// ==================== POI 选择器 & 修改 ====================

const openPoiSelector = (msg) => {
  if (msg.categories) {
    poiSelectorData.categories = msg.categories
    poiSelectorData.selected = { ...msg.selected }
  }
  poiSelectorVisible.value = true
}

const findPoisMsgAndOpen = (mapMsg) => {
  const idx = chatMessages.value.indexOf(mapMsg)
  for (let i = idx; i >= 0; i--) {
    const m = chatMessages.value[i]
    if (m.type === 'pois' && m.categories) {
      openPoiSelector(m)
      return
    }
  }
  alert('无法找到 POI 数据，请刷新页面重试')
}

const handlePoiConfirm = async (selectedPois) => {
  poiSelectorVisible.value = false
  if (selectedPois.length < 2) return
  if (!modifyLocation.value) {
    chatMessages.value.push({ content: '无法确定约会地点，请重新发起约会规划', isUser: false })
    return
  }
  connecting.value = true
  const aiIndex = chatMessages.value.length
  chatMessages.value.push({ content: '正在重新规划路线...', isUser: false })
  try {
    const { promise } = regeneratePlan({
      selectedPois, location: modifyLocation.value,
      budget: '', style: '', convId: activeId.value,
    })
    const response = await promise
    if (!response.ok) {
      chatMessages.value[aiIndex].content = '重新规划失败，请重试'
      connecting.value = false
      return
    }
    const handler = createLoveEventHandler(aiIndex)
    pendingFormCleanup = handler.cleanup
    await consumeSSE(response, {
      ...handler,
      onDone() { connecting.value = false; loveAbort = null },
      onError(err) { chatMessages.value[aiIndex].content = err.message || '出错'; connecting.value = false; loveAbort = null },
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      chatMessages.value[aiIndex].content = '重新规划出错: ' + e.message
    }
    connecting.value = false
  }
}

const showModifyInput = ref(false)
const modifyText = ref('')

const handleAiModify = async () => {
  if (!modifyText.value.trim()) return
  if (!modifyLocation.value) {
    chatMessages.value.push({ content: '无法确定约会地点，请重新发起约会规划', isUser: false })
    showModifyInput.value = false
    return
  }
  const msg = modifyText.value.trim()
  modifyText.value = ''
  showModifyInput.value = false
  chatMessages.value.push({ content: msg, isUser: true })
  connecting.value = true
  const aiIndex = chatMessages.value.length
  chatMessages.value.push({ content: '', isUser: false })
  try {
    const { promise } = modifyPlanStream(msg, modifyLocation.value, resultSelectedPois.value, activeId.value)
    const response = await promise
    if (!response.ok) {
      chatMessages.value[aiIndex].content = '修改请求失败'
      connecting.value = false
      return
    }
    const handler = createLoveEventHandler(aiIndex)
    pendingFormCleanup = handler.cleanup
    await consumeSSE(response, {
      ...handler,
      onDone() { connecting.value = false; loveAbort = null; getConversations().then(list => conversations.value = list) },
      onError(err) { chatMessages.value[aiIndex].content = err.message || '出错'; connecting.value = false; loveAbort = null },
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      chatMessages.value[aiIndex].content = '修改出错: ' + e.message
    }
    connecting.value = false
  }
}

// ==================== 工具函数 ====================

const extractToolName = (message) => {
  if (!message) return 'Agent 工具'
  const m = message.replace(/^完成[：:]\s*/, '')
  if (/搜索|查找/.test(m)) return '高德 POI 搜索'
  if (/路线|步行|规划/.test(m)) return '高德 步行路线规划'
  if (/详情|补充|图片/.test(m)) return 'POI 详情获取'
  if (/PDF|生成|计划书/.test(m)) return 'PDF 生成'
  return 'Agent 工具'
}

const getCatIcon = (label) => {
  if (!label) return '📍'
  if (label.includes('茶') || label.includes('咖啡') || label.includes('休闲')) return '☕'
  if (label.includes('景点') || label.includes('公园') || label.includes('观景')) return '🌿'
  if (label.includes('餐') || label.includes('火锅') || label.includes('美食')) return '🍽️'
  if (label.includes('甜品') || label.includes('甜蜜')) return '🍰'
  if (label.includes('花店') || label.includes('浪漫') || label.includes('惊喜')) return '💐'
  if (label.includes('书店') || label.includes('文艺') || label.includes('展览')) return '📚'
  if (label.includes('酒吧') || label.includes('微醺')) return '🍷'
  return '📍'
}

</script>

<style scoped>
.chat-page { display: flex; height: 100vh; position: relative; z-index: 2; }

.sidebar-overlay { display: none; }

.chat-main { flex: 1; display: flex; min-width: 0; transition: all 0.3s ease; }

/* ======== 简单对话模式：ChatPanel 占满 ======== */
.chat-main:not(.agent-mode) { flex-direction: column; }
.chat-main:not(.agent-mode) > :deep(.chat-panel) { flex: 1; }

/* ======== Agent 模式：两栏并排（聊天 | 结果） ======== */
.chat-main.agent-mode { flex-direction: row; }
.chat-main.agent-mode > :deep(.chat-panel) { width: 340px; flex-shrink: 0; }
.chat-main.agent-mode > :deep(.result-panel) { flex: 1; min-width: 340px; }

/* ======== 移动端 ======== */
@media (max-width: 768px) {
  .sidebar-overlay {
    display: block; position: fixed; inset: 0;
    background: rgba(0,0,0,0.3); z-index: 99;
    opacity: 0; pointer-events: none; transition: opacity 0.3s;
  }
  .sidebar-overlay.show { opacity: 1; pointer-events: auto; }

  .chat-main.agent-mode { flex-direction: column; }
  .chat-main.agent-mode > :deep(.chat-panel) { display: none; }
  .chat-main.agent-mode > :deep(.result-panel) { flex: 1; }
}
</style>
