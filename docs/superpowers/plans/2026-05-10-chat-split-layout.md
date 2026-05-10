# 聊天页左右分栏重构 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 Chat.vue (1326行单体) 拆分为 ChatPage + ChatPanel + ThinkingPanel + ResultPanel 四主件，实现智能自适应三栏布局。

**架构：** ChatPage 作为顶层容器持有 SSE 事件路由 + 模式切换。简单对话时只显示 ChatPanel，收到 plan 事件自动展开三栏。状态通过 provide/inject 共享，无新依赖。

**技术栈：** Vue 3 + Vue Router + Vite，纯 CSS 动画 (300ms ease)

---

### 任务 1：创建 ChatPage.vue 容器

**文件：**
- 创建：`src/views/ChatPage.vue`
- 修改：`src/router/index.js`
- 保留：`src/views/Chat.vue` (暂不删除，逐步迁移)

- [ ] **步骤 1：创建 ChatPage 骨架**

```vue
<template>
  <div class="chat-page">
    <Sidebar
      :conversations="conversations"
      :activeId="activeId"
      :sidebarOpen="sidebarOpen"
      @toggle="sidebarOpen = !sidebarOpen"
      @new="newConversation"
      @switch="switchConversation"
      @delete="deleteConv"
    />

    <div class="chat-main" :class="{ 'agent-mode': mode === 'agent' }">
      <ChatPanel
        v-show="mode === 'chat' || mode === 'agent'"
        :messages="chatMessages"
        :connecting="connecting"
        :text="text"
        @send="send"
        @update:text="text = $event"
      />
      <ThinkingPanel
        v-if="mode === 'agent'"
        :steps="thinkingBlocks"
        :streaming="connecting"
      />
      <ResultPanel
        v-if="mode === 'agent'"
        :steps="planSteps"
        :activeTab="resultTab"
        :pois="resultPois"
        :selectedPois="resultSelectedPois"
        :routeInfo="resultRouteInfo"
        :pdfUrl="resultPdfUrl"
      />
    </div>

    <DynamicForm v-if="activeFormSpec" :formSpec="activeFormSpec"
      @submit="handleFormSubmit" @close="activeFormSpec = null" />
  </div>
</template>
```

- [ ] **步骤 2：复制 Chat.vue 的 script 逻辑到 ChatPage，精简为状态 + 路由**

```js
import { ref, reactive, watch, nextTick, onMounted, provide } from 'vue'
import { getConversations, getMessages, createConversation, deleteConversation,
  routeIntent, loveStreamInit, loveStreamResume, regeneratePlan, modifyPlanStream } from '../api'
import { consumeSSE } from '../utils/sse'

// --- 会话 ---
const conversations = ref([])
const activeId = ref('')
const sidebarOpen = ref(false)

// --- 聊天 ---
const chatMessages = ref([])
const text = ref('')
const connecting = ref(false)

// --- Agent 模式 ---
const mode = ref('chat')           // 'chat' | 'agent'
const thinkingBlocks = ref([])     // [{text, status}]
const planSteps = ref([])          // [{label, status, detail}]
const resultTab = ref('plan')      // 'plan' | 'tools' | 'map' | 'pdf'
const resultPois = ref([])
const resultSelectedPois = ref([])
const resultRouteInfo = ref(null)
const resultPdfUrl = ref(null)

// --- LoveAgent ---
const activeFormSpec = ref(null)
let loveAbort = null
let pendingFormCleanup = null

// --- POI 修改 ---
const poiSelectorVisible = ref(false)
const poiSelectorData = reactive({ categories: [], selected: {} })
const modifyLocation = ref('')

// provide 给子组件
provide('agentState', {
  mode, thinkingBlocks, planSteps, resultTab, connecting,
  resultPois, resultSelectedPois, resultRouteInfo, resultPdfUrl,
})
```

- [ ] **步骤 3：迁移 SSE 事件处理函数（applyLoveEvent 改为写入 provide 的 reactive state）**

```js
const applyLoveEvent = (parsed, target, state) => {
  // text → chatMessages
  if (parsed.type === 'text' && parsed.content) {
    if (state.aiIndex != null && target[state.aiIndex]) {
      target[state.aiIndex].content += parsed.content
    } else {
      target.push({ content: parsed.content, isUser: false })
    }
  }
  // plan → planSteps + thinkingBlocks
  if (parsed.type === 'plan' && parsed.steps) {
    mode.value = 'agent'  // ← 关键：触发展开
    planSteps.value = parsed.steps.map((s, i) => ({
      label: s.message || s.text || `步骤 ${i + 1}`,
      status: s.status || 'pending',
      detail: '',
    }))
    thinkingBlocks.value = parsed.steps.map((s, i) => ({
      text: s.message || s.text || `步骤 ${i + 1}`,
      status: 'pending',
    }))
    resultTab.value = 'plan'
  }
  // step → 更新 planSteps + thinkingBlocks
  if (parsed.type === 'step') {
    const idx = parsed.index
    if (idx != null && idx < planSteps.value.length) {
      planSteps.value[idx].status = parsed.status || planSteps.value[idx].status
      planSteps.value[idx].label = parsed.message || planSteps.value[idx].label
    }
    if (idx != null && idx < thinkingBlocks.value.length) {
      thinkingBlocks.value[idx].status = parsed.status || 'pending'
      thinkingBlocks.value[idx].text = parsed.message || thinkingBlocks.value[idx].text
    }
  }
  // section → resultPois + resultTab='tools'
  if (parsed.type === 'section' && parsed.items) {
    resultPois.value = [...resultPois.value, ...parsed.items]
    resultTab.value = 'tools'
  }
  // map → resultSelectedPois + resultRouteInfo + resultTab='map'
  if (parsed.type === 'map') {
    resultSelectedPois.value = parsed.pois || []
    resultRouteInfo.value = parsed.routeInfo || null
    if (parsed.location) modifyLocation.value = parsed.location
    resultTab.value = 'map'
  }
  // pdf → resultPdfUrl + resultTab='pdf'
  if (parsed.type === 'pdf' && parsed.url) {
    resultPdfUrl.value = parsed.url
    resultTab.value = 'pdf'
  }
  // done → 重置 connecting
  if (parsed.type === 'done') {
    connecting.value = false
    loveAbort = null
  }
}
```

- [ ] **步骤 4：迁移 send / handleLoveStream / handleNormalChat / handleFormSubmit 等顶层方法（从 Chat.vue 原文照搬，只改状态变量名）**

```js
const send = async () => {
  if (!text.value.trim() || connecting.value) return
  const msg = text.value.trim()
  text.value = ''
  chatMessages.value.push({ content: msg, isUser: true })
  try {
    const { intent } = await routeIntent(msg)
    if (intent === 'plan') {
      await handleLoveStream(msg)
    } else {
      handleNormalChat(msg)
    }
  } catch (e) {
    handleNormalChat(msg)
  }
}
// handleNormalChat、handleLoveStream、handleFormSubmit 从 Chat.vue 原文迁移
// 只需要把 messages → chatMessages, rightPanel.* → result*
```

- [ ] **步骤 5：更新路由指向 ChatPage**

```js
// src/router/index.js 中把 '/chat' 路由改为懒加载 ChatPage
{
  path: '/chat',
  name: 'Chat',
  component: () => import('../views/ChatPage.vue'),
}
```

- [ ] **步骤 6：验证 ChatPage 能在浏览器渲染**

运行：`cd yu-ai-agent-frontend && npm run dev`
预期：页面渲染，侧边栏和输入框可见（子组件是空壳暂不报错）

- [ ] **步骤 7：Commit**

```bash
git add src/views/ChatPage.vue src/router/index.js
git commit -m "feat: add ChatPage container with SSE routing logic

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### 任务 2：抽取 Sidebar 组件

**文件：**
- 创建：`src/components/Sidebar.vue`

- [ ] **步骤 1：从 Chat.vue 提取 Sidebar 模板和样式**

Sidebar 纯展示组件，props 接收数据，emits 发出事件：

```vue
<script setup>
defineProps({
  conversations: { type: Array, default: () => [] },
  activeId: { type: String, default: '' },
  sidebarOpen: { type: Boolean, default: false },
})
defineEmits(['toggle', 'new', 'switch', 'delete'])
</script>
```

模板和样式从 Chat.vue 的 `<aside class="sidebar">` 区块完整复制，把所有 `sidebarOpen` 切换逻辑改为 emit。

- [ ] **步骤 2：在 ChatPage 中引用 Sidebar**

```
import Sidebar from '../components/Sidebar.vue'
```

- [ ] **步骤 3：验证侧边栏功能**

预期：点击菜单按钮展开/收起，新建对话、切换对话、删除对话均正常。

- [ ] **步骤 4：Commit**

---

### 任务 3：抽取 ChatPanel 组件

**文件：**
- 创建：`src/components/ChatPanel.vue`
- 创建：`src/components/MessageBubble.vue`
- 创建：`src/components/InputBar.vue`

- [ ] **步骤 1：创建 MessageBubble.vue**

```vue
<template>
  <div class="bubble-row" :class="{ 'user-row': msg.isUser }">
    <!-- text -->
    <template v-if="!msg.type || msg.type === 'text'">
      <div v-if="!msg.isUser" class="avatar ai-avatar"><!-- AI 图标 --></div>
      <div class="bubble" :class="{ user: msg.isUser, ai: !msg.isUser }">
        <div class="bubble-content">{{ msg.content }}</div>
      </div>
      <div v-if="msg.isUser" class="avatar user-avatar"><span>我</span></div>
    </template>
    <!-- 其他 type 暂不处理，后续任务逐步迁移 -->
  </div>
</template>

<script setup>
defineProps({ msg: { type: Object, required: true } })
</script>
```

样式从 Chat.vue 的 `.bubble-row` / `.bubble` / `.avatar` / `.user-avatar` / `.ai-avatar` / `.typing-bubble` 搬过来。

- [ ] **步骤 2：创建 InputBar.vue**

```vue
<template>
  <div class="input-container">
    <div class="input-wrapper glass-card">
      <textarea ref="textareaRef" :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
        @keydown.enter.prevent="$emit('send')"
        placeholder="说点什么吧..." class="input-textarea"
        :disabled="disabled" rows="1"></textarea>
      <button class="send-btn" :class="{ active: modelValue.trim() }"
        @click="$emit('send')" :disabled="disabled || !modelValue.trim()">
        <!-- 发送图标 -->
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const props = defineProps({
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})
defineEmits(['update:modelValue', 'send'])
const textareaRef = ref(null)
</script>
```

样式从 Chat.vue `.input-container` / `.input-wrapper` / `.input-textarea` / `.send-btn` 搬过来。

- [ ] **步骤 3：创建 ChatPanel.vue 组装消息列表 + 输入框**

```vue
<template>
  <div class="chat-panel">
    <div class="chat-topbar">
      <button class="menu-btn glass-btn" @click="$emit('toggleSidebar')"><!-- 汉堡图标 --></button>
      <div class="topbar-center">
        <div class="topbar-avatar"><!-- AI 图标 --></div>
        <div class="topbar-info">
          <h2>AI 恋爱大师</h2>
          <span class="status-dot"></span><span class="status-text">在线</span>
        </div>
      </div>
      <button class="home-btn glass-btn" @click="$router.push('/')"><!-- 首页图标 --></button>
    </div>

    <div class="chat-messages" ref="messagesRef">
      <MessageBubble v-for="(msg, i) in messages" :key="i" :msg="msg" />
      <div v-if="connecting" class="bubble-row">
        <div class="avatar ai-avatar"><!-- AI 图标 --></div>
        <div class="bubble ai typing-bubble">
          <span class="dot"></span><span class="dot"></span><span class="dot"></span>
        </div>
      </div>
    </div>

    <InputBar v-model="inputText" :disabled="connecting" @send="$emit('send')" />
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { smoothScrollToBottom } from '../utils/spring'
import MessageBubble from './MessageBubble.vue'
import InputBar from './InputBar.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  connecting: { type: Boolean, default: false },
  modelValue: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue', 'send', 'toggleSidebar'])

const messagesRef = ref(null)
const inputText = ref(props.modelValue)

watch(() => props.modelValue, v => inputText.value = v)
watch(inputText, v => emit('update:modelValue', v))
watch(() => props.messages.length, () => nextTick(() => smoothScrollToBottom(messagesRef.value)))
</script>
```

样式：`.chat-panel` 占满父容器，`.chat-messages` flex:1 overflow-y:auto。

- [ ] **步骤 4：在 ChatPage 中接入 ChatPanel**

```vue
<ChatPanel
  v-model="text"
  :messages="chatMessages"
  :connecting="connecting"
  @send="send"
  @toggleSidebar="sidebarOpen = !sidebarOpen"
/>
```

- [ ] **步骤 5：验证基础聊天功能**

预期：发送消息 → 气泡出现 → AI 回复 text → 气泡追加内容。简单对话模式（非 plan）正常。

- [ ] **步骤 6：Commit**

---

### 任务 4：创建 ThinkingPanel 组件

**文件：**
- 创建：`src/components/ThinkingPanel.vue`

- [ ] **步骤 1：创建 ThinkingPanel**

```vue
<template>
  <div class="thinking-panel">
    <div class="thinking-header">
      <span class="thinking-dot"></span>
      <span class="thinking-title">思考过程</span>
      <span v-if="streaming" class="thinking-badge">运行中</span>
    </div>

    <div class="thinking-body" ref="bodyRef">
      <div v-for="(block, i) in blocks" :key="i" class="think-block" :class="block.status">
        <div class="think-indicator">
          <span v-if="block.status === 'done'" class="dot done">✓</span>
          <span v-else-if="block.status === 'active'" class="dot active"></span>
          <span v-else class="dot pending"></span>
        </div>
        <div class="think-content">
          <div class="think-text">{{ block.text }}</div>
        </div>
      </div>
      <div v-if="blocks.length === 0 && !streaming" class="think-empty">
        等待 Agent 启动...
      </div>
    </div>
  </div>
</template>

<script setup>
import { watch, nextTick, ref } from 'vue'

defineProps({
  blocks: { type: Array, default: () => [] },  // [{text, status: 'pending'|'active'|'done'}]
  streaming: { type: Boolean, default: false },
})

const bodyRef = ref(null)
watch(() => props.blocks.length, () => nextTick(() => {
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}))
</script>

<style scoped>
.thinking-panel {
  width: 320px; flex-shrink: 0; display: flex; flex-direction: column;
  background: rgba(255,255,255,0.06); border-left: 1px solid rgba(255,255,255,0.1);
  border-right: 1px solid rgba(255,255,255,0.1);
}
.thinking-header {
  display: flex; align-items: center; gap: 8px;
  padding: 12px 16px; border-bottom: 1px solid rgba(255,255,255,0.1);
}
.thinking-dot { width: 6px; height: 6px; border-radius: 50%; background: #ff6b9d; }
.thinking-title { font-size: 0.82rem; font-weight: 600; }
.thinking-badge { font-size: 0.65rem; padding: 2px 8px; border-radius: 10px;
  background: rgba(255,107,157,0.15); color: #ff6b9d; margin-left: auto; }
.thinking-body { flex: 1; overflow-y: auto; padding: 12px; }
.think-block { display: flex; gap: 10px; padding: 8px 0; border-left: 2px solid transparent; padding-left: 10px; }
.think-block.active { border-left-color: #ff6b9d; }
.think-block.done { border-left-color: #4ecca3; opacity: 0.7; }
.think-block.pending { border-left-color: #333; opacity: 0.5; }
.think-indicator { flex-shrink: 0; width: 16px; }
.dot { display: inline-block; width: 16px; height: 16px; border-radius: 50%;
  text-align: center; line-height: 16px; font-size: 9px; }
.dot.active { background: #ff6b9d; animation: pulse 1.5s infinite; }
.dot.done { background: #4ecca3; color: #fff; }
.dot.pending { border: 2px solid #555; }
.think-text { font-size: 0.78rem; color: #ccc; line-height: 1.5; }
.think-empty { color: #555; font-size: 0.78rem; text-align: center; padding: 32px 0; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.4} }
</style>
```

- [ ] **步骤 2：在 ChatPage 中接入**

```vue
<ThinkingPanel
  v-if="mode === 'agent'"
  :blocks="thinkingBlocks"
  :streaming="connecting"
/>
```

放在 ChatPanel 和 ResultPanel 之间。

- [ ] **步骤 3：测试 plan → thinking 流**

发一条约会规划消息 → `routeIntent` 返回 `intent: 'plan'` → `handleLoveStream` 启动 → 收到 `plan` 事件 → `mode='agent'` → ThinkingPanel 显示步骤列表。

预期：ThinkingPanel 中看到步骤卡片逐条出现，状态从 pending→active→done。

- [ ] **步骤 4：Commit**

---

### 任务 5：改造 RightPanel 为 ResultPanel（Tab 系统）

**文件：**
- 创建：`src/components/ResultPanel.vue`
- 保留：`src/components/RightPanel.vue` (暂留作参考，最后清理)

- [ ] **步骤 1：创建 ResultPanel**

```vue
<template>
  <div class="result-panel">
    <div class="result-header">
      <span class="result-title">执行结果</span>
      <button class="result-close" @click="$emit('close')">✕</button>
    </div>

    <!-- Tab 栏 -->
    <div class="result-tabs">
      <button v-for="tab in tabs" :key="tab.key"
        class="result-tab" :class="{ active: activeTab === tab.key }"
        @click="$emit('update:activeTab', tab.key)">
        {{ tab.icon }} {{ tab.label }}
      </button>
    </div>

    <!-- Tab 内容 -->
    <div class="result-body">
      <!-- 计划 Tab -->
      <div v-show="activeTab === 'plan'" class="tab-content">
        <StepProgress v-if="steps.length" :steps="steps" />
        <div v-else class="empty-tab">等待执行计划...</div>
      </div>

      <!-- 工具 Tab -->
      <div v-show="activeTab === 'tools'" class="tab-content">
        <div v-if="pois.length" class="poi-grid">
          <PoiCard v-for="(poi, i) in pois" :key="i" :poi="poi" />
        </div>
        <div v-else class="empty-tab">暂无搜索结果</div>
      </div>

      <!-- 地图 Tab -->
      <div v-show="activeTab === 'map'" class="tab-content">
        <RouteMap v-if="selectedPois.length >= 2" :pois="selectedPois" :routeInfo="routeInfo" />
        <div v-else class="empty-tab">等待路线规划...</div>
      </div>

      <!-- PDF Tab -->
      <div v-show="activeTab === 'pdf'" class="tab-content">
        <div v-if="pdfUrl" class="pdf-card glass-card">
          <div class="pdf-icon">📄</div>
          <div class="pdf-info">
            <span class="pdf-title">约会计划书</span>
            <span class="pdf-desc">PDF 已生成</span>
          </div>
          <a :href="getFullPdfUrl(pdfUrl)" class="glass-btn primary pdf-btn" download>下载</a>
        </div>
        <div v-else class="empty-tab">PDF 尚未生成</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import StepProgress from './StepProgress.vue'
import RouteMap from './RouteMap.vue'
import PoiCard from './PoiCard.vue'

const API_BASE = process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8123/api'

const props = defineProps({
  activeTab: { type: String, default: 'plan' },
  steps: { type: Array, default: () => [] },
  pois: { type: Array, default: () => [] },
  selectedPois: { type: Array, default: () => [] },
  routeInfo: { type: Object, default: null },
  pdfUrl: { type: String, default: '' },
})
defineEmits(['update:activeTab', 'close'])

const tabs = [
  { key: 'plan', icon: '📋', label: '计划' },
  { key: 'tools', icon: '🔧', label: '工具' },
  { key: 'map', icon: '🗺️', label: '地图' },
  { key: 'pdf', icon: '📄', label: 'PDF' },
]

const getFullPdfUrl = (path) => {
  if (!path) return '#'
  return API_BASE.replace('/api', '') + path
}
</script>

<style scoped>
.result-panel {
  flex: 1; display: flex; flex-direction: column; min-width: 280px;
  background: rgba(255,255,255,0.04); border-left: 1px solid rgba(255,255,255,0.1);
}
.result-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid rgba(255,255,255,0.1);
}
.result-title { font-size: 0.82rem; font-weight: 600; }
.result-close { background: none; border: none; color: #888; cursor: pointer; font-size: 14px; }
.result-tabs { display: flex; gap: 4px; padding: 8px 12px; border-bottom: 1px solid rgba(255,255,255,0.06); }
.result-tab {
  padding: 5px 12px; border-radius: 8px; font-size: 0.75rem;
  background: transparent; border: none; color: #888; cursor: pointer;
  transition: all 0.2s;
}
.result-tab.active { background: rgba(255,107,157,0.12); color: #ff6b9d; }
.result-tab:hover:not(.active) { color: #ccc; }
.result-body { flex: 1; overflow-y: auto; padding: 12px; }
.tab-content { min-height: 100%; }
.empty-tab { color: #555; font-size: 0.78rem; text-align: center; padding: 40px 0; }
.poi-grid { display: flex; flex-direction: column; gap: 8px; }
/* PDF 卡片 */
.pdf-card { display: flex; align-items: center; gap: 12px; padding: 14px; }
.pdf-icon { font-size: 24px; }
.pdf-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.pdf-title { font-size: 0.85rem; font-weight: 600; }
.pdf-desc { font-size: 0.75rem; color: #888; }
.pdf-btn { font-size: 13px; padding: 8px 16px; text-decoration: none; flex-shrink: 0; }
</style>
```

- [ ] **步骤 2：在 ChatPage 中接入 ResultPanel**

```vue
<ResultPanel
  v-if="mode === 'agent'"
  :activeTab="resultTab"
  :steps="planSteps"
  :pois="resultPois"
  :selectedPois="resultSelectedPois"
  :routeInfo="resultRouteInfo"
  :pdfUrl="resultPdfUrl"
  @update:activeTab="resultTab = $event"
  @close="mode = 'chat'"
/>
```

- [ ] **步骤 3：验证 SSE 事件 → Tab 自动切换**

预期：收到 `plan` → Tab 切到「计划」；收到 `section` → Tab 切到「工具」并显示 POI；收到 `map` → Tab 切到「地图」；收到 `pdf` → Tab 切到「PDF」。

- [ ] **步骤 4：Commit**

---

### 任务 6：ChatPage 布局样式（三栏 + 动画）

**文件：**
- 修改：`src/views/ChatPage.vue` (加 scoped style)

- [ ] **步骤 1：添加三栏布局 CSS**

```css
.chat-page { display: flex; height: 100vh; position: relative; z-index: 2; }

/* 聊天主区 — 简单模式填满，Agent 模式收窄 */
.chat-main {
  flex: 1; display: flex; min-width: 0;
  transition: all 0.3s ease;
}

/* ChatPanel 在简单模式下占满，Agent 模式下占 280px */
.chat-main > .chat-panel {
  flex: 0 0 280px; width: 280px; transition: flex 0.3s ease;
}
.chat-main:not(.agent-mode) > .chat-panel {
  flex: 1; width: auto;
}

/* ThinkingPanel 固定宽度 */
.chat-main > .thinking-panel {
  width: 320px; flex-shrink: 0;
  animation: slideIn 0.3s ease both;
}

/* ResultPanel 占剩余空间 */
.chat-main > .result-panel {
  flex: 1; min-width: 280px;
  animation: slideIn 0.3s ease 0.05s both;
}

@keyframes slideIn {
  from { opacity: 0; transform: translateX(16px); }
  to { opacity: 1; transform: translateX(0); }
}
```

- [ ] **步骤 2：移动端 TabBar**

```css
@media (max-width: 768px) {
  .chat-main { flex-direction: column; }
  .chat-main.agent-mode > .chat-panel,
  .chat-main.agent-mode > .thinking-panel { display: none; }
  .chat-main.agent-mode > .result-panel { flex: 1; display: flex; }

  /* 底部 TabBar */
  .mobile-tabbar {
    display: flex; position: fixed; bottom: 0; left: 0; right: 0;
    background: rgba(0,0,0,0.85); backdrop-filter: blur(12px);
    border-top: 1px solid rgba(255,255,255,0.1); z-index: 100;
  }
  .mobile-tabbar .tab {
    flex: 1; padding: 10px; text-align: center; font-size: 0.7rem; color: #888; border: none; background: none;
  }
  .mobile-tabbar .tab.active { color: #ff6b9d; }
  .mobile-tabbar .tab .badge { display: inline-block; width: 6px; height: 6px; border-radius: 50%;
    background: #ff6b9d; margin-left: 3px; vertical-align: super; }
}

@media (min-width: 769px) {
  .mobile-tabbar { display: none; }
}
```

在 ChatPage 模板底部添加移动端 TabBar：
```html
<div class="mobile-tabbar" v-if="mode === 'agent'">
  <button class="tab" :class="{ active: mobileTab === 'chat' }" @click="mobileTab = 'chat'">
    💬 聊天 <span v-if="mobileTab !== 'chat' && connecting" class="badge"></span>
  </button>
  <button class="tab" :class="{ active: mobileTab === 'think' }" @click="mobileTab = 'think'">
    🧠 思考
  </button>
  <button class="tab" :class="{ active: mobileTab === 'result' }" @click="mobileTab = 'result'">
    🔧 结果
  </button>
</div>
```

- [ ] **步骤 3：浏览器 resize 测试**

预期：桌面端三栏并排，<768px 切换为单屏 + 底部 TabBar。

- [ ] **步骤 4：Commit**

---

### 任务 7：清理 & 收尾

**文件：**
- 删除：`src/views/Chat.vue`
- 删除：`src/components/RightPanel.vue`
- 修改：所有引用旧路径的地方

- [ ] **步骤 1：确认所有功能迁移完毕**

检查清单：
- [x] 简单对话（text 气泡）
- [x] Agent 执行（plan/step → ThinkingPanel + ResultPanel）
- [x] POI 选择器 → 重新规划
- [x] AI 修改约会
- [x] 表单弹窗
- [x] PDF 下载
- [x] 地图路线
- [x] 移动端适配

- [ ] **步骤 2：删除旧文件**

```bash
rm src/views/Chat.vue src/components/RightPanel.vue
```

- [ ] **步骤 3：全局搜索残留引用**

```bash
grep -r "Chat.vue\|RightPanel" src/
```
预期：无结果。

- [ ] **步骤 4：完整功能测试**

运行 `npm run dev`，执行完整的约会规划流程：
1. 发消息 "帮我规划上海外滩的约会"
2. 观察三栏展开，思考过程出现
3. 观察 ResultPanel Tab 自动切换
4. 等待地图和 PDF 出现
5. 测试简单 Q&A（如 "什么是 RAG？"），确认不展开三栏

- [ ] **步骤 5：Commit**

---

### 任务 8：使用 ui-ux-pro-max 打磨视觉

**文件：**
- 可能修改：`src/components/ResultPanel.vue`、`src/components/ThinkingPanel.vue`、`src/components/ChatPanel.vue`、`src/styles/glassmorphism.css`

- [ ] **步骤 1：检查当前设计**

调用 `ui-ux-pro-max` 技能 review 现有组件，获取配色、字体、间距等建议。

- [ ] **步骤 2：应用优化建议**

根据 ui-ux-pro-max 反馈调整：
- 配色方案是否统一
- 字体层级是否清晰
- 间距 / 留白是否合理
- 暗色模式下可读性
- 动效是否流畅

- [ ] **步骤 3：Commit**

---

## 文件变更总览

| 操作 | 文件 |
|------|------|
| 新建 | `src/views/ChatPage.vue` |
| 新建 | `src/components/Sidebar.vue` |
| 新建 | `src/components/ChatPanel.vue` |
| 新建 | `src/components/MessageBubble.vue` |
| 新建 | `src/components/InputBar.vue` |
| 新建 | `src/components/ThinkingPanel.vue` |
| 新建 | `src/components/ResultPanel.vue` |
| 修改 | `src/router/index.js` |
| 删除 | `src/views/Chat.vue` |
| 删除 | `src/components/RightPanel.vue` |
| 不改 | `src/api/index.js`, `src/utils/sse.js`, `src/utils/spring.js`, `src/components/StepProgress.vue`, `src/components/RouteMap.vue`, `src/components/PoiCard.vue`, `src/components/PoiSelector.vue`, `src/components/DynamicForm.vue` |
