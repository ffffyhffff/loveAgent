<template>
  <div class="input-container">
    <div class="input-wrapper glass-card">
      <textarea ref="textareaRef"
        :value="modelValue"
        @input="onInput"
        @keydown.enter.prevent="handleEnter"
        placeholder="说点什么吧..." class="input-textarea"
        :disabled="disabled" rows="1"></textarea>
      <button class="send-btn" :class="{ active: modelValue.trim() }"
              @click="$emit('send')" :disabled="disabled || !modelValue.trim()">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <path d="M22 2L11 13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          <path d="M22 2L15 22L11 13L2 9L22 2Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
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

const emit = defineEmits(['update:modelValue', 'send'])

const textareaRef = ref(null)

const onInput = (e) => {
  emit('update:modelValue', e.target.value)
  autoResize()
}

const handleEnter = (e) => {
  if (!e.shiftKey) {
    emit('send')
  }
}

const autoResize = () => {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 100) + 'px'
}

defineExpose({ textareaRef })
</script>

<style scoped>
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
</style>
