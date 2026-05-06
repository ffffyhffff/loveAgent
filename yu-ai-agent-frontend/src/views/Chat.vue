<template>
  <div class="chat-page">
    <!-- 侧边栏遮罩 -->
    <div class="sidebar-overlay" :class="{ show: sidebarOpen }" @click="sidebarOpen = false"></div>

    <!-- 侧边栏 -->
    <aside class="sidebar glass-sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-header">
        <div class="sidebar-logo">
          <div class="logo-dot"></div>
          <span>LoveAgent</span>
        </div>
        <button class="new-btn glass-btn primary" @click="newConversation">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M12 5V19M5 12H19" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/>
          </svg>
          新对话
        </button>
      </div>
      <div class="conv-list">
        <div v-for="conv in conversations" :key="conv.id"
             class="conv-item" :class="{ active: conv.id === activeId }"
             @click="switchConversation(conv.id)">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" class="conv-icon">
            <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
          </svg>
          <div class="conv-info">
            <span class="conv-title">{{ conv.title }}</span>
            <span class="conv-count">{{ conv.messageCount }} 条消息</span>
          </div>
          <button class="del-btn" @click.stop="deleteConv(conv.id)">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none">
              <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
        <div v-if="conversations.length === 0" class="empty-tip">还没有对话</div>
      </div>
    </aside>

    <!-- 主区域 -->
    <div class="chat-main">
      <div class="chat-topbar">
        <button class="menu-btn glass-btn" @click="sidebarOpen = !sidebarOpen">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M3 12H21M3 6H21M3 18H21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
        <div class="topbar-center">
          <div class="topbar-dot"></div>
          <span>AI 恋爱大师</span>
          <span class="status">在线</span>
        </div>
        <button class="glass-btn" @click="$router.push('/')" style="font-size:12px; padding:6px 12px;">
          首页
        </button>
      </div>

      <div class="chat-messages" ref="messagesRef">
        <div v-for="(msg, i) in messages" :key="i"
             class="bubble-row" :class="{ 'user-row': msg.isUser }">
          <div v-if="!msg.isUser" class="avatar ai-av">AI</div>
          <div class="bubble" :class="{ user: msg.isUser, ai: !msg.isUser }">
            <div class="bubble-content">{{ msg.content }}</div>
          </div>
          <div v-if="msg.isUser" class="avatar user-av">我</div>
        </div>
        <div v-if="connecting" class="bubble-row">
          <div class="avatar ai-av">AI</div>
          <div class="bubble ai typing-bubble">
            <span class="dot"></span><span class="dot"></span><span class="dot"></span>
          </div>
        </div>
      </div>

      <div class="input-container">
        <div class="input-wrapper glass-card">
          <textarea ref="textareaRef" v-model="text"
            @keydown.enter.prevent="handleEnter" @input="autoResize"
            placeholder="输入你的恋爱问题..."
            class="input-textarea" :disabled="connecting" rows="1"></textarea>
          <button class="send-btn" :class="{ active: text.trim() }"
                  @click="send" :disabled="connecting || !text.trim()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M22 2L11 13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick, onMounted } from 'vue'
import { chatSSE, getConversations, getMessages, createConversation, deleteConversation } from '../api'
import { smoothScrollToBottom } from '../utils/spring'

const messagesRef = ref(null)
const textareaRef = ref(null)
const text = ref('')
const connecting = ref(false)
const sidebarOpen = ref(false)
const conversations = ref([])
const activeId = ref('')
const messages = ref([])

onMounted(async () => { await loadConversations() })

const loadConversations = async () => {
  try {
    conversations.value = await getConversations()
    if (conversations.value.length > 0) await switchConversation(conversations.value[0].id)
    else await newConversation()
  } catch(e) { console.error(e) }
}
const loadMessages = async (convId) => {
  try {
    const data = await getMessages(convId)
    messages.value = data.map(m => ({ content: m.content, isUser: m.isUser === 'true' }))
    nextTick(scrollToBottom)
  } catch(e) { console.error(e) }
}
const switchConversation = async (id) => {
  activeId.value = id; sidebarOpen.value = false; await loadMessages(id)
}
const newConversation = async () => {
  try {
    const { id } = await createConversation()
    conversations.value = await getConversations()
    activeId.value = id; messages.value = []; sidebarOpen.value = false
  } catch(e) { console.error(e) }
}
const deleteConv = async (id) => {
  try {
    await deleteConversation(id)
    conversations.value = await getConversations()
    if (activeId.value === id) {
      if (conversations.value.length > 0) await switchConversation(conversations.value[0].id)
      else await newConversation()
    }
  } catch(e) { console.error(e) }
}

const scrollToBottom = () => smoothScrollToBottom(messagesRef.value)
watch(() => messages.value.length, scrollToBottom)
watch(() => messages.value.map(m => m.content).join(''), scrollToBottom)

const send = () => {
  if (!text.value.trim() || connecting.value) return
  messages.value.push({ content: text.value, isUser: true })
  const aiIndex = messages.value.length
  messages.value.push({ content: '', isUser: false })
  connecting.value = true
  const msg = text.value; text.value = ''
  if (textareaRef.value) textareaRef.value.style.height = 'auto'
  const es = chatSSE(msg, activeId.value)
  es.onmessage = (event) => {
    const data = event.data
    if (data === '[DONE]') { connecting.value = false; es.close(); getConversations().then(l => conversations.value = l); return }
    if (aiIndex < messages.value.length) messages.value[aiIndex].content += data
  }
  es.onerror = () => {
    if (messages.value[aiIndex] && !messages.value[aiIndex].content) messages.value[aiIndex].content = '连接出错，请重试'
    connecting.value = false; es.close()
  }
}
const handleEnter = (e) => { if (!e.shiftKey) send(); else text.value += '\n' }
const autoResize = () => {
  const el = textareaRef.value; if (!el) return
  el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 100) + 'px'
}
</script>

<style scoped>
.chat-page { display: flex; height: 100vh; position: relative; z-index: 2; }
.sidebar-overlay { display: none; }

.sidebar {
  width: 260px; height: 100vh;
  display: flex; flex-direction: column; flex-shrink: 0;
  background: rgba(0,0,0,0.35);
  backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px);
  border-right: 1px solid var(--border);
}
.sidebar-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px; border-bottom: 1px solid var(--border);
}
.sidebar-logo { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; font-weight: 600; }
.logo-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent-gradient); }
.new-btn { display: flex; align-items: center; gap: 4px; font-size: 11px; padding: 5px 10px; }

.conv-list { flex: 1; overflow-y: auto; padding: 8px; display: flex; flex-direction: column; gap: 2px; }
.conv-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 12px; border-radius: 10px; cursor: pointer;
  transition: all 0.25s var(--spring);
}
.conv-item:hover { background: var(--bg-glass-hover); }
.conv-item.active { background: rgba(168,85,247,0.1); border: 1px solid rgba(168,85,247,0.15); }
.conv-icon { flex-shrink: 0; color: var(--text-dim); }
.conv-item.active .conv-icon { color: var(--accent-1); }
.conv-info { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.conv-title { font-size: 0.8rem; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.conv-count { font-size: 0.65rem; color: var(--text-dim); }
.del-btn {
  opacity: 0; background: none; border: none; color: var(--text-dim);
  cursor: pointer; padding: 4px; border-radius: 4px; transition: all 0.2s;
}
.conv-item:hover .del-btn { opacity: 1; }
.del-btn:hover { color: #ef4444; background: rgba(239,68,68,0.1); }
.empty-tip { text-align: center; color: var(--text-dim); font-size: 0.75rem; padding: 40px 0; }

.chat-main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-topbar {
  display: flex; align-items: center; gap: 12px; padding: 10px 16px;
  background: rgba(0,0,0,0.2); backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border); z-index: 10;
}
.menu-btn { padding: 6px; border-radius: 8px; display: none; }
.topbar-center { flex: 1; display: flex; align-items: center; gap: 8px; font-size: 0.85rem; font-weight: 600; }
.topbar-dot { width: 6px; height: 6px; border-radius: 50%; background: #22c55e; }
.status { font-size: 0.65rem; color: #22c55e; font-weight: 400; }

.chat-messages { flex: 1; overflow-y: auto; padding: 20px 16px; display: flex; flex-direction: column; gap: 12px; }
.bubble-row { display: flex; align-items: flex-end; gap: 10px; max-width: 80%; animation: springIn 0.5s var(--spring) both; }
.bubble-row.user-row { align-self: flex-end; }

.avatar {
  width: 28px; height: 28px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; font-size: 10px; font-weight: 700;
}
.ai-av { background: rgba(168,85,247,0.15); color: var(--accent-1); border: 1px solid rgba(168,85,247,0.2); }
.user-av { background: rgba(99,102,241,0.15); color: var(--accent-2); border: 1px solid rgba(99,102,241,0.2); }

.bubble { padding: 10px 14px; border-radius: 14px; line-height: 1.6; font-size: 14px; word-break: break-word; }
.bubble.ai { background: var(--ai-bubble); border: 1px solid var(--ai-border); border-bottom-left-radius: 4px; }
.bubble.user { background: var(--user-bubble); border: 1px solid var(--user-border); border-bottom-right-radius: 4px; }
.bubble-content { white-space: pre-wrap; }

.typing-bubble { display: flex; align-items: center; gap: 5px; padding: 12px 16px; }
.typing-bubble .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent-1); animation: typing 1.4s infinite; }
.typing-bubble .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-bubble .dot:nth-child(3) { animation-delay: 0.4s; }

.input-container { padding: 12px 16px 16px; }
.input-wrapper { display: flex; align-items: flex-end; gap: 10px; padding: 6px 6px 6px 14px; border-radius: 18px; }
.input-textarea {
  flex: 1; background: transparent; border: none; outline: none;
  color: var(--text-primary); font-size: 14px; line-height: 1.5;
  resize: none; max-height: 100px; padding: 8px 0; font-family: inherit;
}
.input-textarea::placeholder { color: var(--text-dim); }
.input-textarea:disabled { opacity: 0.4; }
.send-btn {
  width: 36px; height: 36px; border-radius: 10px;
  border: 1px solid var(--border); background: var(--bg-glass);
  color: var(--text-dim); display: flex; align-items: center; justify-content: center;
  cursor: pointer; flex-shrink: 0; transition: all 0.3s var(--spring);
}
.send-btn.active { background: rgba(168,85,247,0.15); border-color: rgba(168,85,247,0.3); color: var(--accent-1); }
.send-btn:hover:not(:disabled) { transform: scale(1.1); }
.send-btn:active:not(:disabled) { transform: scale(0.9); }
.send-btn:disabled { opacity: 0.3; cursor: not-allowed; }

@media (max-width: 768px) {
  .menu-btn { display: flex; }
  .sidebar { position: fixed; left: -280px; top: 0; z-index: 100; transition: left 0.3s var(--spring); box-shadow: 4px 0 24px rgba(0,0,0,0.3); }
  .sidebar.open { left: 0; }
  .sidebar-overlay { display: block; position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 99; opacity: 0; pointer-events: none; transition: opacity 0.3s; }
  .sidebar-overlay.show { opacity: 1; pointer-events: auto; }
}
</style>
