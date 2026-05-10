<template>
  <div class="step-progress glass-card">
    <h4 class="progress-title">约会规划进度</h4>
    <div class="steps">
      <div v-for="(step, i) in steps" :key="i"
           class="step-item" :class="step.status">
        <div class="step-indicator">
          <div class="step-dot">
            <svg v-if="step.status === 'done'" width="12" height="12" viewBox="0 0 24 24" fill="none">
              <path d="M5 13L9 17L19 7" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <div v-else-if="step.status === 'active'" class="spinner"></div>
          </div>
          <div v-if="i < steps.length - 1" class="step-line"></div>
        </div>
        <div class="step-content">
          <span class="step-label">{{ step.label }}</span>
          <span class="step-detail" v-if="step.detail">{{ step.detail }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  steps: { type: Array, default: () => [] },  // [{label, status: 'pending'|'active'|'done', detail?}]
})
</script>

<style scoped>
.step-progress { padding: 16px 20px; margin: 8px 0; }
.progress-title { font-size: 0.9rem; font-weight: 600; margin-bottom: 14px; }
.steps { display: flex; flex-direction: column; gap: 0; }

.step-item { display: flex; gap: 12px; }
.step-indicator { display: flex; flex-direction: column; align-items: center; }
.step-dot {
  width: 24px; height: 24px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid var(--glass-border);
  background: var(--glass-bg);
  transition: all 0.3s var(--transition-spring);
}
.step-item.done .step-dot {
  background: rgba(76, 175, 80, 0.15); border-color: #4caf50; color: #4caf50;
}
.step-item.active .step-dot {
  background: rgba(233, 30, 99, 0.12); border-color: #e91e63;
}
.step-line {
  width: 2px; height: 28px; flex-shrink: 0;
  background: var(--glass-border); margin: 4px 0;
  transition: background 0.3s;
}
.step-item.done .step-line { background: #4caf50; }

.spinner {
  width: 12px; height: 12px; border-radius: 50%;
  border: 2px solid rgba(233,30,99,0.2); border-top-color: #e91e63;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.step-content {
  display: flex; flex-direction: column; padding-bottom: 12px;
  min-height: 40px; justify-content: center;
}
.step-label { font-size: 0.82rem; font-weight: 500; }
.step-detail { font-size: 0.72rem; color: var(--color-text-secondary); margin-top: 2px; }
.step-item.pending .step-label { color: var(--color-text-dim); }
</style>

