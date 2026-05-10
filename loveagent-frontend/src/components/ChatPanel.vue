<template>
  <div class="chat-panel">
    <div class="chat-topbar">
      <button class="menu-btn glass-btn" @click="$emit('toggleSidebar')">
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
      <MessageBubble v-for="(msg, i) in messages" :key="i" :msg="msg"
        @changePoi="$emit('changePoi', $event)"
        @aiModify="$emit('aiModify', $event)" />
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

    <InputBar :modelValue="modelValue" :disabled="connecting"
      @update:modelValue="$emit('update:modelValue', $event)"
      @send="$emit('send')" />
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
const emit = defineEmits(['update:modelValue', 'send', 'toggleSidebar', 'changePoi', 'aiModify'])

const messagesRef = ref(null)
const scrollToBottom = () => { smoothScrollToBottom(messagesRef.value) }

watch(() => props.messages.length, scrollToBottom)
watch(() => props.messages.map(m => m.content).join(''), scrollToBottom)
</script>

<style scoped>
.chat-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; }

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

/* 打字动画 */
.typing-bubble { display: flex; align-items: center; gap: 5px; padding: 13px 16px; }
.typing-bubble .dot { width: 7px; height: 7px; border-radius: 50%; background: #e91e63; animation: typing 1.4s infinite; }
.typing-bubble .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-bubble .dot:nth-child(3) { animation-delay: 0.4s; }

.bubble.ai { background: rgba(255,255,255,0.5); border: 1px solid rgba(255,255,255,0.45); border-bottom-left-radius: 4px; }
.bubble { padding: 11px 15px; border-radius: 16px; line-height: 1.6; font-size: 14px; word-break: break-word; }

@media (max-width: 768px) {
  .menu-btn { display: flex; }
}
</style>
