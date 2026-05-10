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
import { ref, watch, nextTick } from 'vue'

const props = defineProps({
  blocks: { type: Array, default: () => [] },
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
  animation: slideIn 0.3s ease both;
}
@keyframes slideIn {
  from { opacity: 0; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}
.thinking-header {
  display: flex; align-items: center; gap: 8px;
  padding: 12px 16px; border-bottom: 1px solid rgba(255,255,255,0.1);
  flex-shrink: 0;
}
.thinking-dot { width: 6px; height: 6px; border-radius: 50%; background: #ff6b9d; }
.thinking-title { font-size: 0.82rem; font-weight: 600; }
.thinking-badge {
  font-size: 0.62rem; padding: 2px 8px; border-radius: 10px;
  background: rgba(255,107,157,0.15); color: #ff6b9d; margin-left: auto;
}
.thinking-body { flex: 1; overflow-y: auto; padding: 12px; }
.think-block { display: flex; gap: 8px; padding: 6px 0; border-left: 2px solid transparent; padding-left: 10px; margin-bottom: 2px; }
.think-block.active { border-left-color: #ff6b9d; }
.think-block.done { border-left-color: #4ecca3; opacity: 0.7; }
.think-block.pending { border-left-color: rgba(255,255,255,0.08); opacity: 0.5; }
.think-indicator { flex-shrink: 0; width: 14px; padding-top: 1px; }
.dot { display: inline-block; width: 14px; height: 14px; border-radius: 50%; text-align: center; line-height: 14px; font-size: 8px; }
.dot.active { background: #ff6b9d; animation: pulse 1.5s infinite; }
.dot.done { background: #4ecca3; color: #fff; }
.dot.pending { border: 2px solid #555; }
.think-text { font-size: 0.76rem; color: #ccc; line-height: 1.5; }
.think-empty { color: #555; font-size: 0.76rem; text-align: center; padding: 40px 0; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.4} }
</style>
