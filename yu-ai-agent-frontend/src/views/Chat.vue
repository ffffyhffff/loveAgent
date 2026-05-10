<template>
  <div class="chat-page">
    <!-- 渚ц竟鏍忛伄缃?-->
    <div class="sidebar-overlay" :class="{ show: sidebarOpen }" @click="sidebarOpen = false"></div>

    <!-- 渚ц竟鏍?-->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-header">
        <h3>聊天记录</h3>
        <button class="new-btn glass-btn" @click="newConversation">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none">
            <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/>
          </svg>
          新对话
        </button>
      </div>
      <div class="conv-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conv-item"
          :class="{ active: conv.id === activeId }"
          @click="switchConversation(conv.id)"
        >
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" class="conv-icon">
            <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
          </svg>
          <div class="conv-info">
            <span class="conv-title">{{ conv.title }}</span>
            <span class="conv-count">{{ conv.messageCount }} 条消息</span>
          </div>
          <button class="del-btn" @click.stop="deleteConv(conv.id)" title="删除">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        <div v-if="conversations.length === 0" class="empty-tip">还没有对话记录</div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="chat-main">
      <div class="chat-topbar">
        <button class="menu-btn glass-btn" @click="sidebarOpen = !sidebarOpen">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
            <path d="M3 12H21M3 6H21M3 18H21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
        <div class="topbar-center">
          <div class="topbar-avatar">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="6" r="3" fill="currentColor"/>
              <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
              <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
              <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
            </svg>
          </div>
          <div class="topbar-info">
            <h2>AI 恋爱大师</h2>
            <span class="status-dot"></span><span class="status-text">在线</span>
          </div>
        </div>
        <button class="home-btn glass-btn" @click="$router.push('/')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M3 9.5L12 3L21 9.5V20C21 20.5304 20.7893 21.0391 20.4142 21.4142C20.0391 21.7893 19.5304 22 19 22H5C4.46957 22 3.96086 21.7893 3.58579 21.4142C3.21071 21.0391 3 20.5304 3 20V9.5Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>

      <div class="chat-messages" ref="messagesRef">
        <div v-for="(msg, i) in messages" :key="i"
             class="bubble-row" :class="{ 'user-row': msg.isUser }">
          <!-- 普通消息 -->
          <template v-if="!msg.type || msg.type === 'text'">
            <div v-if="!msg.isUser" class="avatar ai-avatar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="6" r="3" fill="currentColor"/>
                <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
                <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              </svg>
            </div>
            <div class="bubble" :class="{ user: msg.isUser, ai: !msg.isUser }">
              <div class="bubble-content">{{ msg.content }}</div>
            </div>
            <div v-if="msg.isUser" class="avatar user-avatar"><span>我</span></div>
          </template>


          <!-- 步骤进度 -->
          <template v-else-if="msg.type === 'steps'">
            <div class="avatar ai-avatar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="6" r="3" fill="currentColor"/>
                <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
                <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              </svg>
            </div>
            <StepProgress :steps="msg.steps" />
          </template>

          <!-- 地图 -->
          <template v-else-if="msg.type === 'map'">
            <div class="avatar ai-avatar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="6" r="3" fill="currentColor"/>
                <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
                <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              </svg>
            </div>
            <div class="map-bubble-wrapper">
              <RouteMap :pois="msg.pois" :routeInfo="msg.routeInfo" />
              <!-- 淇敼鎸夐挳 -->
              <div class="map-actions">
                <button class="action-btn" @click="findPoisMsgAndOpen(msg)">
                  换目的地
                </button>
                <button class="action-btn" @click="showModifyInput = !showModifyInput">
                  让 AI 改
                </button>
              </div>
              <!-- AI 修改输入框 -->
              <div v-if="showModifyInput" class="modify-input-bar">
                <input v-model="modifyText" placeholder="例如：换成茶馆，晚餐想吃火锅..."
                       class="modify-input" @keyup.enter="handleAiModify" />
                <button class="modify-send" @click="handleAiModify">发送</button>
              </div>
            </div>
          </template>

          <!-- 执行面板（紧凑模式） -->
          <template v-else-if="msg.type === 'execution' && msg.execution">
            <div class="avatar ai-avatar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="6" r="3" fill="currentColor"/>
                <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
                <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              </svg>
            </div>
            <div class="exec-panel glass-card">
              <div class="exec-header">执行计划</div>
              <!-- 步骤列表 -->
              <div v-for="(step, i) in msg.execution.steps" :key="'s'+i" class="exec-step" :class="step.status">
                <span v-if="step.status === 'done'" class="step-icon">✅</span>
                <span v-else-if="step.status === 'active'" class="step-icon spinning">⏳</span>
                <span v-else class="step-icon">○</span>
                <span class="step-text">{{ step.text }}</span>
              </div>
              <!-- 搜索结果 -->
              <div v-for="(result, i) in msg.execution.results" :key="'r'+i" class="exec-result">
                <div class="exec-result-header">{{ result.icon }} {{ result.label }}</div>
                <div v-for="(poi, j) in result.items" :key="j" class="exec-poi">
                  <span class="poi-name">{{ poi.name }}</span>
                  <span class="poi-meta">
                    <span v-if="poi.distance">{{ poi.distance }}m</span>
                  </span>
                </div>
              </div>
              <!-- 璇︽儏 -->
              <div v-for="(detail, i) in msg.execution.details" :key="'d'+i" class="exec-detail">
                <div class="detail-header">📷 {{ detail.name }}</div>
                <div v-if="detail.text" class="detail-info">{{ detail.text }}</div>
                <div v-if="detail.images && detail.images.length" class="detail-images">
                  <img v-for="(img, k) in detail.images.slice(0,3)" :key="k" :src="img" class="detail-img" loading="lazy" />
                </div>
              </div>
            </div>
          </template>

          <!-- 分类结果展示（POI 卡片） -->
          <template v-else-if="msg.type === 'section'">
            <div class="avatar ai-avatar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="6" r="3" fill="currentColor"/>
                <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
                <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              </svg>
            </div>
            <div class="section-card glass-card">
              <div class="section-title">{{ msg.icon }} {{ msg.title }}</div>
              <PoiCard v-for="(poi, j) in msg.items" :key="j" :poi="poi" />
            </div>
          </template>

          <!-- 地点详情 -->
          <template v-else-if="msg.type === 'placeDetail'">
            <div class="avatar ai-avatar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="6" r="3" fill="currentColor"/>
                <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
                <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              </svg>
            </div>
            <div class="place-detail-card glass-card">
              <div class="place-detail-media" v-if="msg.images && msg.images.length">
                <img :src="msg.images[0]" :alt="msg.placeName" loading="lazy" />
              </div>
              <div class="place-detail-body">
                <div class="place-detail-kicker">{{ msg.keyword || '行程点' }}</div>
                <div class="place-detail-name">{{ msg.placeName }}</div>
                <div class="place-detail-address">{{ msg.address }}</div>
                <div class="place-detail-meta">
                  <span v-if="msg.rating">评分 {{ msg.rating }}</span>
                  <span v-if="msg.cost">人均 {{ msg.cost }}</span>
                  <span v-if="msg.openTime">{{ msg.openTime }}</span>
                </div>
              </div>
            </div>
          </template>

          <!-- PDF 下载 -->
          <template v-else-if="msg.type === 'pdf'">
            <div class="avatar ai-avatar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="6" r="3" fill="currentColor"/>
                <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
                <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              </svg>
            </div>
            <div class="pdf-card glass-card">
              <div class="pdf-icon">📄</div>
              <div class="pdf-info">
                <span class="pdf-title">约会计划书</span>
                <span class="pdf-desc">PDF 已生成，点击下载</span>
              </div>
              <a :href="getPdfUrl(msg.url)" class="glass-btn primary pdf-btn" download>下载</a>
            </div>
          </template>

          <!-- LoveAgent 动态表单（占位，实际弹窗由 DynamicForm 组件处理） -->
          <template v-else-if="msg.type === 'form'">
            <div class="avatar ai-avatar">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
                <circle cx="12" cy="6" r="3" fill="currentColor"/>
                <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
                <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
                <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              </svg>
            </div>
            <div class="bubble ai form-placeholder-bubble">
              <span class="form-waiting-text">📋 {{ msg.formSpec?.title || '请填写信息' }} - 弹窗已打开</span>
            </div>
          </template>
        </div>

        <!-- 打字动画 -->
        <div v-if="connecting" class="bubble-row">
          <div class="avatar ai-avatar">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="6" r="3" fill="currentColor"/>
              <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
              <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
              <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
              <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
            </svg>
          </div>
          <div class="bubble ai typing-bubble">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>

      <div class="input-container">
        <div class="input-wrapper glass-card">
          <textarea ref="textareaRef" v-model="text"
            @keydown.enter.prevent="handleEnter" @input="autoResize"
            placeholder="说点什么吧..." class="input-textarea"
            :disabled="connecting" rows="1"></textarea>
          <button class="send-btn" :class="{ active: text.trim() }"
                  @click="send" :disabled="connecting || !text.trim()">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M22 2L11 13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- LoveAgent 动态表单弹窗-->
    <DynamicForm
      v-if="activeFormSpec"
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

    <!-- 鍙充晶鎵ц闈㈡澘 -->
    <RightPanel
      :visible="rightPanelVisible"
      :steps="rightPanel.steps"
      :pois="rightPanel.pois"
      :selectedPois="rightPanel.selectedPois"
      :routeInfo="rightPanel.routeInfo"
      :pdfUrl="rightPanel.pdfUrl"
      @close="rightPanelVisible = false"
    />
  </div>
</template>

<script setup>
import { ref, reactive, watch, nextTick, onMounted } from 'vue'
import {
  chatSSE, getConversations, getMessages, createConversation, deleteConversation,
  routeIntent, loveStreamInit, loveStreamResume, regeneratePlan, modifyPlanStream,
} from '../api'
import { smoothScrollToBottom } from '../utils/spring'
import { consumeSSE } from '../utils/sse'
import StepProgress from '../components/StepProgress.vue'
import RouteMap from '../components/RouteMap.vue'
import DynamicForm from '../components/DynamicForm.vue'
import PoiSelector from '../components/PoiSelector.vue'
import PoiCard from '../components/PoiCard.vue'
import RightPanel from '../components/RightPanel.vue'

const API_BASE = process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8123/api'

const messagesRef = ref(null)
const textareaRef = ref(null)
const text = ref('')
const connecting = ref(false)
const sidebarOpen = ref(false)

const conversations = ref([])
const activeId = ref('')
const messages = ref([])

// LoveAgent state
const activeFormSpec = ref(null)
const rightPanelVisible = ref(false)
const rightPanel = reactive({
  steps: [],
  pois: [],
  selectedPois: [],
  routeInfo: null,
  pdfUrl: null,
})
let loveAbort = null

// POI modification state
const poiSelectorVisible = ref(false)
const poiSelectorData = reactive({ categories: [], selected: {} })
const modifyLocation = ref('') // 当前约会计划的地点（用于修改时搜索）

// Open POI selector
const openPoiSelector = (msg) => {
  if (msg.categories) {
    poiSelectorData.categories = msg.categories
    poiSelectorData.selected = { ...msg.selected }
  }
  poiSelectorVisible.value = true
}

// 纭淇敼 POI
const handlePoiConfirm = async (selectedPois) => {
  poiSelectorVisible.value = false
  if (selectedPois.length < 2) return
  if (!modifyLocation.value) {
    messages.value.push({ content: '无法确定约会地点，请重新发起约会规划', isUser: false })
    return
  }

  connecting.value = true
  const aiIndex = messages.value.length
  messages.value.push({ content: '正在重新规划路线...', isUser: false })

  try {
    const { promise } = regeneratePlan({
      selectedPois,
      location: modifyLocation.value,
      budget: '',
      style: '',
      convId: activeId.value,
    })
    const response = await promise
    if (!response.ok) {
      messages.value[aiIndex].content = '重新规划失败，请重试'
      connecting.value = false
      return
    }

    const handler = createLoveEventHandler(aiIndex)
    pendingFormCleanup = handler.cleanup
    await consumeSSE(response, {
      ...handler,
      onDone() { connecting.value = false; loveAbort = null },
      onError(err) { messages.value[aiIndex].content = err.message || '鍑洪敊'; connecting.value = false; loveAbort = null },
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      messages.value[aiIndex].content = '重新规划出错: ' + e.message
    }
    connecting.value = false
  }
}

// AI 淇敼瀵硅瘽
const showModifyInput = ref(false)
const modifyText = ref('')

const handleAiModify = async () => {
  if (!modifyText.value.trim()) return
  if (!modifyLocation.value) {
    messages.value.push({ content: '无法确定约会地点，请重新发起约会规划', isUser: false })
    showModifyInput.value = false
    return
  }
  const msg = modifyText.value.trim()
  modifyText.value = ''
  showModifyInput.value = false

  messages.value.push({ content: msg, isUser: true })
  connecting.value = true
  const aiIndex = messages.value.length
  messages.value.push({ content: '', isUser: false })

  try {
    const { promise } = modifyPlanStream(msg, modifyLocation.value)
    const response = await promise
    if (!response.ok) {
      messages.value[aiIndex].content = '修改请求失败'
      connecting.value = false
      return
    }

    const handler = createLoveEventHandler(aiIndex)
    pendingFormCleanup = handler.cleanup
    await consumeSSE(response, {
      ...handler,
      onDone() { connecting.value = false; loveAbort = null; getConversations().then(list => conversations.value = list) },
      onError(err) { messages.value[aiIndex].content = err.message || '鍑洪敊'; connecting.value = false; loveAbort = null },
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      messages.value[aiIndex].content = '淇敼鍑洪敊: ' + e.message
    }
    connecting.value = false
  }
}

onMounted(async () => {
  await loadConversations()
})

const loadConversations = async () => {
  try {
    conversations.value = await getConversations()
    if (conversations.value.length > 0) {
      await switchConversation(conversations.value[0].id)
    } else {
      await newConversation()
    }
  } catch(e) {
    console.error('加载对话列表失败', e)
  }
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
        } catch {
          // Ignore malformed saved event payloads.
        }
      } else {
        loaded.push({
          content: m.content,
          isUser: m.isUser === 'true',
        })
      }
    }
    messages.value = loaded
    nextTick(scrollToBottom)
  } catch(e) {
    console.error('加载消息失败', e)
  }
}

// Cleanup callback for delayed form popup
let pendingFormCleanup = null

const createExecutionMessage = () => ({
  content: '',
  isUser: false,
  type: 'execution',
  execution: { steps: [], results: [], details: [] },
})

const syncRightPanelSteps = (steps) => {
  rightPanel.steps = steps.map((step, index) => ({
    label: step.text || step.label || `步骤 ${index + 1}`,
    detail: step.detail || '',
    status: step.status || 'pending',
  }))
}

const applyLoveEvent = (parsed, target, state) => {
  const getExecMsg = () => {
    if (!state.execMsg) {
      state.execMsg = createExecutionMessage()
      target.push(state.execMsg)
    }
    return state.execMsg
  }

  if (parsed.type === 'text' && parsed.content) {
    if (state.execMsg || rightPanel.pdfUrl || rightPanel.selectedPois.length) {
      target.push({ content: parsed.content, isUser: false })
    } else if (state.aiIndex != null && target[state.aiIndex]) {
      target[state.aiIndex].content += parsed.content
    } else {
      target.push({ content: parsed.content, isUser: false })
    }
  }
  if (parsed.type === 'plan' && parsed.steps) {
    const msg = getExecMsg()
    msg.execution.steps = parsed.steps.map((step, index) => ({
      text: step.message || step.text || `步骤 ${index + 1}`,
      status: step.status || 'pending',
    }))
  }
  if (parsed.type === 'plan' && parsed.steps) {
    syncRightPanelSteps(state.execMsg.execution.steps)
  }
  if (parsed.type === 'step') {
    const msg = getExecMsg()
    const steps = msg.execution.steps
    if (Number.isInteger(parsed.index)) {
      while (steps.length <= parsed.index) {
        steps.push({ text: '', status: 'pending' })
      }
      steps[parsed.index] = {
        text: parsed.status === 'done' ? (steps[parsed.index].text || parsed.message) : (parsed.message || steps[parsed.index].text),
        status: parsed.status || steps[parsed.index].status || 'pending',
      }
      syncRightPanelSteps(steps)
      return
    }
    if (parsed.status === 'pending') {
      steps.push({ text: parsed.message, status: 'pending' })
    } else if (parsed.status === 'active') {
      for (let i = 0; i < steps.length; i++) {
        if (steps[i].status === 'pending') { steps[i].status = 'active'; break }
      }
    } else if (parsed.status === 'done') {
      for (let i = 0; i < steps.length; i++) {
        if (steps[i].status === 'active') { steps[i].status = 'done'; break }
      }
    }
    syncRightPanelSteps(steps)
  }
  if (parsed.type === 'section' && parsed.items) {
    const msg = getExecMsg()
    msg.execution.results.push({
      icon: parsed.icon || getCatIcon(parsed.title),
      label: parsed.title,
      items: parsed.items.slice(0, 3),
    })
  }
  if (parsed.type === 'review') {
    const msg = getExecMsg()
    msg.execution.details.push({
      name: parsed.placeName || '',
      text: parsed.content || '',
      images: parsed.images || [],
    })
  }
  if (parsed.type === 'placeDetail') {
    const msg = getExecMsg()
    msg.execution.details.push({
      name: parsed.placeName || '',
      text: [parsed.address, parsed.rating ? `评分 ${parsed.rating}` : '', parsed.cost ? `人均 ${parsed.cost}` : '']
        .filter(Boolean)
        .join(' 路 '),
      images: parsed.images || [],
    })
    target.push({
      content: '',
      isUser: false,
      type: 'placeDetail',
      keyword: parsed.keyword,
      placeName: parsed.placeName,
      address: parsed.address,
      rating: parsed.rating,
      cost: parsed.cost,
      openTime: parsed.openTime,
      images: parsed.images || [],
    })
  }
  if (parsed.type === 'pois' && parsed.categories) {
    rightPanel.pois = parsed.categories.flatMap(c => c.items || [])
    target.push({
      content: '',
      isUser: false,
      type: 'pois',
      categories: parsed.categories,
      selected: parsed.selected || {},
    })
  }
  if (parsed.type === 'map') {
    rightPanel.selectedPois = parsed.pois || []
    rightPanel.routeInfo = parsed.routeInfo || null
    if (parsed.location) modifyLocation.value = parsed.location
    target.push({ content: '', isUser: false, type: 'map', pois: parsed.pois || [], routeInfo: parsed.routeInfo || null })
  }
  if (parsed.type === 'pdf' && parsed.url) {
    rightPanel.pdfUrl = parsed.url
    target.push({ content: '', isUser: false, type: 'pdf', url: parsed.url })
  }
}

const appendLoveEventsToMessages = (events, target, aiIndex = null) => {
  const state = { execMsg: null, aiIndex }
  for (const evt of events) {
    applyLoveEvent(evt, target, state)
  }
  return state
}

const switchConversation = async (id) => {
  if (pendingFormCleanup) { pendingFormCleanup(); pendingFormCleanup = null }
  activeFormSpec.value = null
  activeId.value = id
  sidebarOpen.value = false
  await loadMessages(id)
}

const newConversation = async () => {
  try {
    if (pendingFormCleanup) { pendingFormCleanup(); pendingFormCleanup = null }
    activeFormSpec.value = null
    const { id } = await createConversation()
    conversations.value = await getConversations()
    activeId.value = id
    messages.value = []
    sidebarOpen.value = false
  } catch(e) {
    console.error('创建对话失败', e)
  }
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
  } catch(e) {
    console.error('删除对话失败', e)
  }
}

const scrollToBottom = () => { smoothScrollToBottom(messagesRef.value) }
watch(() => messages.value.length, scrollToBottom)
watch(() => messages.value.map(m => m.content).join(''), scrollToBottom)
watch(() => JSON.stringify(rightPanel.steps), scrollToBottom)
watch(() => messages.value.map(m => JSON.stringify(m.execution || {})).join(''), scrollToBottom)

// 主发送逻辑
const send = async () => {
  if (!text.value.trim() || connecting.value) return

  const msg = text.value.trim()
  text.value = ''
  if (textareaRef.value) textareaRef.value.style.height = 'auto'

  messages.value.push({ content: msg, isUser: true })

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

// ========== 普通对话 ==========
const handleNormalChat = (msg) => {
  const aiIndex = messages.value.length
  messages.value.push({ content: '', isUser: false })
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
    if (parsed.type === 'text' && parsed.content && aiIndex < messages.value.length) {
      messages.value[aiIndex].content += parsed.content
    }
    if (parsed.type === 'error' && parsed.message) {
      if (aiIndex < messages.value.length) messages.value[aiIndex].content = parsed.message
      connecting.value = false
      es.close()
    }
  }

  es.onerror = () => {
    if (messages.value[aiIndex] && !messages.value[aiIndex].content) {
      messages.value[aiIndex].content = '连接出错了，请重试'
    }
    connecting.value = false
    es.close()
  }
}

// ========== LoveAgent 公共 SSE 事件处理 ===================
const createLoveEventHandler = (aiIndex) => {
  let formTimer = null
  let execMsg = null
  const realtimeState = { execMsg: null, aiIndex }

  const getExecMsg = () => {
    if (!execMsg) {
      execMsg = { content: '', isUser: false, type: 'execution', execution: { steps: [], results: [], details: [] } }
      messages.value.push(execMsg)
    }
    return execMsg
  }

  const handler = {
  onEvent(parsed) {
    if (parsed.type !== 'form') {
      applyLoveEvent(parsed, messages.value, realtimeState)
      return
    }
    if (parsed.type === 'text' && parsed.content) {
      if (execMsg || rightPanel.pdfUrl || rightPanel.selectedPois.length) {
        messages.value.push({ content: parsed.content, isUser: false })
      } else {
        messages.value[aiIndex].content += parsed.content
      }
    }
    if (parsed.type === 'plan' && parsed.steps) {
      const msg = getExecMsg()
      msg.execution.steps = parsed.steps.map((step, index) => ({
        text: step.message || step.text || `步骤 ${index + 1}`,
        status: step.status || 'pending',
      }))
    }
    if (parsed.type === 'form' && parsed.formSpec) {
      let spec = parsed.formSpec
      if (typeof spec === 'string') {
        try { spec = JSON.parse(spec) } catch { return }
      }
      // 如果表单没有字段（信息已完整），跳过弹窗
      if (!spec.fields || spec.fields.length === 0) {
        activeFormSpec.value = null
        return
      }
      // 延迟 2 秒弹窗，让用户先看完 AI 回复
      messages.value.push({ content: '', isUser: false, type: 'form', formSpec: spec })
      if (formTimer) clearTimeout(formTimer)
      formTimer = setTimeout(() => {
        activeFormSpec.value = spec
        formTimer = null
      }, 2000)
    }
    if (parsed.type === 'step') {
      const msg = getExecMsg()
      const steps = msg.execution.steps
      if (Number.isInteger(parsed.index)) {
        while (steps.length <= parsed.index) {
          steps.push({ text: '', status: 'pending' })
        }
        steps[parsed.index] = {
          text: parsed.message || steps[parsed.index].text,
          status: parsed.status || steps[parsed.index].status || 'pending',
        }
        return
      }
      if (parsed.status === 'pending') {
        steps.push({ text: parsed.message, status: 'pending' })
      } else if (parsed.status === 'active') {
        for (let i = 0; i < steps.length; i++) {
          if (steps[i].status === 'pending') { steps[i].status = 'active'; break }
        }
      } else if (parsed.status === 'done') {
        for (let i = 0; i < steps.length; i++) {
          if (steps[i].status === 'active') { steps[i].status = 'done'; break }
        }
      }
    }
    if (parsed.type === 'pois') {
      if (parsed.categories) {
        rightPanel.pois = parsed.categories.flatMap(c => c.items || [])
        messages.value.push({
          content: '',
          isUser: false,
          type: 'pois',
          categories: parsed.categories,
          selected: parsed.selected || {},
        })
      }
    }
    if (parsed.type === 'map') {
      rightPanel.selectedPois = parsed.pois || []
      rightPanel.routeInfo = parsed.routeInfo || null
      if (parsed.location) modifyLocation.value = parsed.location
      messages.value.push({ content: '', isUser: false, type: 'map', pois: parsed.pois || [], routeInfo: parsed.routeInfo || null })
    }
    if (parsed.type === 'pdf' && parsed.url) {
      rightPanel.pdfUrl = parsed.url
      messages.value.push({ content: '', isUser: false, type: 'pdf', url: parsed.url })
    }
    // Search results
    if (parsed.type === 'section' && parsed.items) {
      const msg = getExecMsg()
      msg.execution.results.push({
        icon: parsed.icon || getCatIcon(parsed.title),
        label: parsed.title,
        items: parsed.items.slice(0, 3),
      })
    }
    // Legacy review details
    if (parsed.type === 'review') {
      const msg = getExecMsg()
      msg.execution.details.push({
        name: parsed.placeName || '',
        text: parsed.content || '',
        images: parsed.images || [],
      })
    }
  },
  }
  // 切换对话时清理定时器
  handler.cleanup = () => { if (formTimer) { clearTimeout(formTimer); formTimer = null } }
  return handler
}

// ========== LoveAgent 娴佸紡瀵硅瘽 ==========
const handleLoveStream = async (msg) => {
  connecting.value = true
  const aiIndex = messages.value.length
  messages.value.push({ content: '', isUser: false })

  const { promise, abort } = loveStreamInit(msg, activeId.value)
  loveAbort = abort

  try {
    const response = await promise
    if (!response.ok) {
      messages.value[aiIndex].content = '请求失败，请重试'
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
        messages.value[aiIndex].content = err.message || '请求出错'
        connecting.value = false
        loveAbort = null
      },
    })
  } catch (e) {
    if (e.name !== 'AbortError') {
      console.error('LoveStream 失败', e)
      messages.value[aiIndex].content = '请求出错: ' + e.message
    }
    connecting.value = false
    loveAbort = null
  }
}

// ========== LoveAgent 表单提交 ==========
const handleFormSubmit = ({ formId, answers }) => {
  activeFormSpec.value = null

  const parts = Object.entries(answers)
    .filter(([_, v]) => v !== '' && v !== null && v !== undefined)
    .map(([k, v]) => `${k}：${Array.isArray(v) ? v.join(', ') : v}`)
  if (parts.length > 0) {
    messages.value.push({ content: parts.join('，'), isUser: true })
  }

  connecting.value = true
  const aiIndex = messages.value.length
  messages.value.push({ content: '', isUser: false })

  const { promise, abort } = loveStreamResume(activeId.value, formId, answers)
  loveAbort = abort

  promise.then(response => {
    if (!response.ok) {
      messages.value[aiIndex].content = '请求失败，请重试'
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
        messages.value[aiIndex].content = err.message || '请求出错'
        connecting.value = false
        loveAbort = null
      },
    })
  }).catch(e => {
    if (e.name !== 'AbortError') {
      console.error('LoveStream resume 失败', e)
      messages.value[aiIndex].content = '请求出错: ' + e.message
    }
    connecting.value = false
    loveAbort = null
  })
}

const getPdfUrl = (path) => {
  if (!path) return '#'
  return API_BASE.replace('/api', '') + path
}

// Open the nearest POI selector message
const findPoisMsgAndOpen = (mapMsg) => {
  // 往前找最近的 pois 消息
  const idx = messages.value.indexOf(mapMsg)
  for (let i = idx; i >= 0; i--) {
    const m = messages.value[i]
    if (m.type === 'pois' && m.categories) {
      openPoiSelector(m)
      return
    }
  }
  alert('无法找到 POI 数据，请刷新页面重试')
}

const getCatIcon = (label) => {
  if (!label) return '📍'
  if (label.includes('茶') || label.includes('咖啡') || label.includes('休闲') || label.includes('鑼?') || label.includes('鍜栧暋')) return '☕'
  if (label.includes('景点') || label.includes('公园') || label.includes('观景') || label.includes('鏅偣') || label.includes('鍏洯') || label.includes('瑙傛櫙')) return '🌿'
  if (label.includes('餐') || label.includes('火锅') || label.includes('美食') || label.includes('椁愬巺') || label.includes('鐏攨')) return '🍽️'
  if (label.includes('甜品') || label.includes('甜蜜') || label.includes('鐢滃搧')) return '🍰'
  if (label.includes('花店') || label.includes('浪漫') || label.includes('惊喜') || label.includes('鑺卞簵')) return '💐'
  if (label.includes('书店') || label.includes('文艺') || label.includes('展览') || label.includes('涔﹀簵')) return '📚'
  if (label.includes('酒吧') || label.includes('微醺') || label.includes('閰掑惂')) return '🍷'
  return '📍'
}

const handleEnter = (e) => {
  if (!e.shiftKey) send()
  else text.value += '\n'
}

const autoResize = () => {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 100) + 'px'
}
</script>

<style scoped>
.chat-page { display: flex; height: 100vh; position: relative; z-index: 2; }

/* 渚ц竟鏍忛伄缃?*/
.sidebar-overlay { display: none; }

/* 渚ц竟鏍?*/
.sidebar {
  width: 260px; height: 100vh;
  background: rgba(255,255,255,0.15); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px);
  border-right: 1px solid rgba(255,255,255,0.25);
  display: flex; flex-direction: column; flex-shrink: 0;
}
.sidebar-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px; border-bottom: 1px solid rgba(255,255,255,0.2);
}
.sidebar-header h3 { font-size: 0.9rem; font-weight: 700; }
.new-btn { display: flex; align-items: center; gap: 4px; font-size: 12px; padding: 6px 12px; border-radius: 10px; }

.conv-list { flex: 1; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; gap: 2px; }
.conv-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 12px; cursor: pointer;
  transition: all 0.25s var(--transition-spring); position: relative;
}
.conv-item:hover { background: rgba(255,255,255,0.2); }
.conv-item.active { background: rgba(233,30,99,0.1); border: 1px solid rgba(233,30,99,0.12); }
.conv-icon { flex-shrink: 0; color: var(--color-text-dim); }
.conv-item.active .conv-icon { color: #e91e63; }
.conv-info { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.conv-title { font-size: 0.82rem; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-count { font-size: 0.68rem; color: var(--color-text-dim); margin-top: 1px; }
.del-btn {
  opacity: 0; background: none; border: none; color: var(--color-text-dim);
  cursor: pointer; padding: 4px; border-radius: 6px; transition: all 0.2s;
}
.conv-item:hover .del-btn { opacity: 1; }
.del-btn:hover { color: #e91e63; background: rgba(233,30,99,0.08); }
.empty-tip { text-align: center; color: var(--color-text-dim); font-size: 0.8rem; padding: 40px 0; }

/* 主区域 */
.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-topbar {
  display: flex; align-items: center; gap: 12px; padding: 10px 16px;
  background: rgba(255,255,255,0.18); backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(255,255,255,0.25); z-index: 10;
}
.menu-btn { padding: 8px; border-radius: 10px; display: none; }
.home-btn { padding: 8px; border-radius: 10px; }
.topbar-center { flex: 1; display: flex; align-items: center; gap: 10px; }
.topbar-avatar {
  width: 34px; height: 34px; border-radius: 50%;
  background: rgba(233,30,99,0.1); border: 1px solid rgba(233,30,99,0.15);
  display: flex; align-items: center; justify-content: center; color: #e91e63;
}
.topbar-info h2 { font-size: 0.95rem; font-weight: 700; line-height: 1.2; }
.topbar-info { display: flex; flex-direction: column; }
.status-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #4caf50; margin-right: 3px; vertical-align: middle; }
.status-text { font-size: 0.68rem; color: var(--color-text-secondary); vertical-align: middle; }

/* 消息 */
.chat-messages { flex: 1; overflow-y: auto; padding: 20px 16px; display: flex; flex-direction: column; gap: 12px; }
.bubble-row { display: flex; align-items: flex-end; gap: 10px; max-width: 85%; animation: springIn 0.5s var(--transition-spring) both; }
.bubble-row.user-row { align-self: flex-end; }
.avatar { width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ai-avatar { background: rgba(233,30,99,0.08); color: #e91e63; border: 1px solid rgba(233,30,99,0.12); }
.user-avatar { background: rgba(233,30,99,0.12); color: #e91e63; font-size: 11px; font-weight: 700; border: 1px solid rgba(233,30,99,0.15); }
.bubble { padding: 11px 15px; border-radius: 16px; line-height: 1.6; font-size: 14px; word-break: break-word; }
.bubble.ai { background: rgba(255,255,255,0.5); border: 1px solid rgba(255,255,255,0.45); border-bottom-left-radius: 4px; }
.bubble.user { background: rgba(233,30,99,0.1); border: 1px solid rgba(233,30,99,0.12); border-bottom-right-radius: 4px; }
.bubble-content { white-space: pre-wrap; }
.typing-bubble { display: flex; align-items: center; gap: 5px; padding: 13px 16px; }
.typing-bubble .dot { width: 7px; height: 7px; border-radius: 50%; background: #e91e63; animation: typing 1.4s infinite; }
.typing-bubble .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-bubble .dot:nth-child(3) { animation-delay: 0.4s; }

/* POI 列表卡片 */
.poi-list-card { padding: 16px; margin: 4px 0; }
.poi-list-card h4 { font-size: 0.9rem; font-weight: 600; margin-bottom: 12px; }
.poi-grid { display: flex; flex-direction: column; gap: 8px; }
.poi-item {
  padding: 10px 14px; border-radius: 10px;
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12);
  display: flex; flex-direction: column; gap: 2px;
}
.poi-name { font-size: 0.85rem; font-weight: 600; }
.poi-addr { font-size: 0.75rem; color: var(--color-text-secondary); }
.poi-dist { font-size: 0.72rem; color: var(--color-text-dim); }

/* PDF 鍗＄墖 */
.pdf-card {
  display: flex; align-items: center; gap: 14px;
  padding: 16px; margin: 4px 0;
}
.pdf-icon { font-size: 28px; }
.pdf-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.pdf-title { font-size: 0.9rem; font-weight: 600; }
.pdf-desc { font-size: 0.78rem; color: var(--color-text-secondary); }
.pdf-btn { font-size: 13px; padding: 8px 18px; text-decoration: none; }

/* LoveAgent 表单占位气泡 */
.form-placeholder-bubble { display: flex; align-items: center; }
.form-waiting-text { font-size: 0.82rem; color: var(--color-text-secondary); font-style: italic; }

/* 输入 */
.input-container { padding: 10px 16px 14px; }
.input-wrapper { display: flex; align-items: flex-end; gap: 10px; padding: 8px 8px 8px 16px; border-radius: 20px; }
.input-textarea {
  flex: 1; background: transparent; border: none; outline: none;
  color: var(--color-text); font-size: 14.5px; line-height: 1.5;
  resize: none; max-height: 100px; padding: 8px 0; font-family: inherit;
}
.input-textarea::placeholder { color: var(--color-text-dim); }
.input-textarea:disabled { opacity: 0.5; }
.send-btn {
  width: 38px; height: 38px; border-radius: 12px;
  border: 1px solid rgba(255,255,255,0.3); background: rgba(255,255,255,0.15);
  color: var(--color-text-dim); display: flex; align-items: center; justify-content: center;
  cursor: pointer; flex-shrink: 0; transition: all 0.3s var(--transition-spring);
}
.send-btn.active { background: rgba(233,30,99,0.12); border-color: rgba(233,30,99,0.25); color: #e91e63; }
.send-btn:hover:not(:disabled) { transform: scale(1.1); box-shadow: 0 4px 14px rgba(233,30,99,0.12); }
.send-btn:active:not(:disabled) { transform: scale(0.9); transition: transform 0.1s; }
.send-btn:disabled { opacity: 0.35; cursor: not-allowed; }

/* 聊天气泡内的地图 */
.map-bubble-wrapper {
  width: 100%; max-width: 600px;
  border-radius: 16px; overflow: hidden;
}

@media (max-width: 768px) {
  .menu-btn { display: flex; }
  .sidebar {
    position: fixed; left: -280px; top: 0; z-index: 100;
    transition: left 0.3s var(--transition-spring); box-shadow: 4px 0 24px rgba(0,0,0,0.1);
  }
  .sidebar.open { left: 0; }
  .sidebar-overlay {
    display: block; position: fixed; inset: 0;
    background: rgba(0,0,0,0.3); z-index: 99;
    opacity: 0; pointer-events: none; transition: opacity 0.3s;
  }
  .sidebar-overlay.show { opacity: 1; pointer-events: auto; }
}

/* 地图操作按钮 */
.map-actions {
  display: flex; gap: 8px; margin-top: 10px; padding: 0 4px;
}
.action-btn {
  padding: 8px 14px; border-radius: 10px; font-size: 0.78rem; font-weight: 600;
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: var(--color-text-secondary); cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.action-btn:hover {
  background: linear-gradient(135deg, rgba(139,92,246,0.15), rgba(236,72,153,0.1));
  border-color: rgba(139,92,246,0.3); color: #c084fc;
}

/* AI 修改输入框 */
.modify-input-bar {
  display: flex; gap: 8px; margin-top: 8px;
}
.modify-input {
  flex: 1; padding: 8px 14px; border-radius: 10px; font-size: 0.82rem;
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: var(--color-text); outline: none;
}
.modify-input:focus { border-color: rgba(139,92,246,0.4); }
.modify-input::placeholder { color: rgba(255,255,255,0.3); }
.modify-send {
  padding: 8px 16px; border-radius: 10px; font-size: 0.82rem; font-weight: 600;
  background: linear-gradient(135deg, #8b5cf6, #ec4899); border: none; color: white; cursor: pointer;
}

/* 分类结果卡片 + 评价气泡 */
/* 执行面板（紧凑模式） */
.exec-panel { padding: 14px 16px; margin: 8px 0; max-width: 600px; }
.exec-header {
  font-size: 0.82rem; font-weight: 700; margin-bottom: 10px;
  padding-bottom: 6px; border-bottom: 1px solid rgba(255,255,255,0.08);
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
}
.exec-step {
  display: flex; align-items: center; gap: 8px;
  padding: 4px 0; font-size: 0.78rem;
}
.exec-step.done { color: var(--color-text-secondary); }
.exec-step.active { color: #f59e0b; font-weight: 600; }
.exec-step.pending { color: var(--color-text-dim); }
.step-icon { flex-shrink: 0; width: 18px; text-align: center; }
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }

.exec-result {
  margin-top: 10px; padding: 8px 10px;
  background: rgba(255,255,255,0.04); border-radius: 10px;
}
.exec-result-header { font-size: 0.8rem; font-weight: 600; margin-bottom: 6px; }
.exec-poi {
  display: flex; justify-content: space-between; align-items: center;
  padding: 3px 0; font-size: 0.75rem;
}
.exec-poi .poi-name { color: var(--color-text); }
.exec-poi .poi-meta { color: var(--color-text-dim); font-size: 0.68rem; }

.exec-detail {
  margin-top: 10px; padding: 8px 10px;
  background: rgba(139,92,246,0.06); border-radius: 10px;
  border: 1px solid rgba(139,92,246,0.1);
}
.detail-header { font-size: 0.8rem; font-weight: 600; margin-bottom: 4px; }
.detail-info { font-size: 0.72rem; color: var(--color-text-secondary); white-space: pre-wrap; margin-bottom: 6px; }
.detail-images { display: flex; gap: 6px; }
.detail-img { width: 100px; height: 75px; object-fit: cover; border-radius: 6px; }

.section-card { padding: 14px; margin: 8px 0; }
.section-title {
  font-size: 0.9rem; font-weight: 700; margin-bottom: 8px;
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
}

.review-bubble {
  padding: 12px 16px; margin: 6px 0; border-radius: 14px;
  background: rgba(139,92,246,0.08); border: 1px solid rgba(139,92,246,0.15);
}
.review-place { font-size: 0.8rem; font-weight: 600; margin-bottom: 6px; }
.review-text { font-size: 0.78rem; color: var(--color-text-secondary); white-space: pre-wrap; }
.review-images { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.review-img { width: 120px; height: 90px; object-fit: cover; border-radius: 8px; border: 1px solid rgba(255,255,255,0.1); }

.place-detail-card {
  display: grid;
  grid-template-columns: 132px minmax(0, 1fr);
  gap: 14px;
  padding: 12px;
  max-width: 620px;
  margin: 8px 0;
}
.place-detail-media {
  width: 132px;
  height: 96px;
  border-radius: 8px;
  overflow: hidden;
  background: rgba(255,255,255,0.05);
}
.place-detail-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.place-detail-body { min-width: 0; }
.place-detail-kicker {
  font-size: 0.68rem;
  color: #f59e0b;
  font-weight: 700;
  margin-bottom: 4px;
}
.place-detail-name {
  font-size: 0.92rem;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 5px;
}
.place-detail-address {
  font-size: 0.76rem;
  color: var(--color-text-secondary);
  line-height: 1.45;
  margin-bottom: 8px;
}
.place-detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.place-detail-meta span {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  padding: 3px 7px;
  border-radius: 999px;
  background: rgba(255,255,255,0.06);
}

/* POI 分类样式 */
.poi-cat-section { margin-bottom: 12px; }
.poi-cat-section:last-child { margin-bottom: 0; }
.poi-cat-label {
  font-size: 0.78rem; font-weight: 600; color: var(--color-text-secondary);
  margin-bottom: 6px; padding-bottom: 4px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.poi-selected { border-color: rgba(139,92,246,0.4) !important; background: rgba(139,92,246,0.1) !important; }

/* 寮圭獥閬僵 */
.dialog-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.6); backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center;
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
</style>

