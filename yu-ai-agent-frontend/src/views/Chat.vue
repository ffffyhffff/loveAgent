<template>
  <div class="chat-page">
    <!-- 侧边栏遮罩 -->
    <div class="sidebar-overlay" :class="{ show: sidebarOpen }" @click="sidebarOpen = false"></div>

    <!-- 侧边栏 -->
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
        </div>
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
    messages.value = data.map(m => ({
      content: m.content,
      isUser: m.isUser === 'true',
    }))
    nextTick(scrollToBottom)
  } catch(e) {
    console.error('加载消息失败', e)
  }
}

const switchConversation = async (id) => {
  activeId.value = id
  sidebarOpen.value = false
  await loadMessages(id)
}

const newConversation = async () => {
  try {
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

const send = () => {
  if (!text.value.trim() || connecting.value) return

  messages.value.push({ content: text.value, isUser: true })
  const aiIndex = messages.value.length
  messages.value.push({ content: '', isUser: false })

  connecting.value = true
  const msg = text.value
  text.value = ''
  if (textareaRef.value) textareaRef.value.style.height = 'auto'

  const es = chatSSE(msg, activeId.value)

  es.onmessage = (event) => {
    // 解析 JSON 事件（后端现在发送 JSON）
    let parsed
    try {
      parsed = JSON.parse(event.data)
    } catch (e) {
      // 兼容纯文本
      parsed = { type: 'text', content: event.data }
    }

    if (parsed.type === 'done' || parsed.type === '[DONE]') {
      connecting.value = false
      es.close()
      getConversations().then(list => conversations.value = list)
      return
    }

    if (parsed.type === 'text' && parsed.content) {
      if (aiIndex < messages.value.length) {
        messages.value[aiIndex].content += parsed.content
      }
    }

    if (parsed.type === 'error' && parsed.message) {
      if (aiIndex < messages.value.length) {
        messages.value[aiIndex].content = parsed.message
      }
      connecting.value = false
      es.close()
    }
  }

  es.onerror = () => {
    if (messages.value[aiIndex] && !messages.value[aiIndex].content) {
      messages.value[aiIndex].content = '呜...连接出错了，请重试'
    }
    connecting.value = false
    es.close()
  }
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

/* 侧边栏遮罩 */
.sidebar-overlay { display: none; }

/* 侧边栏 */
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
.bubble-row { display: flex; align-items: flex-end; gap: 10px; max-width: 80%; animation: springIn 0.5s var(--transition-spring) both; }
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
</style>
