<template>
  <div class="plan-confirm glass-card">
    <h3 class="plan-title">{{ plan.question || '确认约会计划？' }}</h3>
    <div class="plan-details" v-if="plan.location">
      <div class="plan-row"><span class="label">📍 地点</span><span>{{ plan.location }}</span></div>
      <div class="plan-row" v-if="plan.budget"><span class="label">💰 预算</span><span>{{ plan.budget }}</span></div>
      <div class="plan-row" v-if="plan.style"><span class="label">✨ 风格</span><span>{{ plan.style }}</span></div>
    </div>
    <div class="plan-steps" v-if="plan.steps && plan.steps.length">
      <div v-for="(step, i) in plan.steps" :key="i" class="step">{{ i + 1 }}. {{ step }}</div>
    </div>
    <div class="plan-actions">
      <button class="glass-btn primary" @click="$emit('confirm', 'approved')">确认</button>
      <button class="glass-btn" @click="$emit('confirm', 'modify')">修改</button>
      <button class="glass-btn" @click="$emit('confirm', 'cancel')">取消</button>
    </div>
  </div>
</template>

<script setup>
defineProps({ plan: Object })
defineEmits(['confirm'])
</script>

<style scoped>
.plan-confirm { padding: 20px; margin: 8px 0; }
.plan-title { font-size: 1rem; margin-bottom: 12px; font-weight: 600; }
.plan-details { margin-bottom: 12px; }
.plan-row { display: flex; gap: 8px; padding: 4px 0; font-size: 0.85rem; }
.label { font-weight: 600; min-width: 50px; }
.plan-steps { margin-bottom: 16px; padding-left: 8px; }
.step { font-size: 0.82rem; color: var(--color-text-secondary); padding: 2px 0; }
.plan-actions { display: flex; gap: 8px; }
.plan-actions button { font-size: 13px; padding: 8px 18px; }
</style>
