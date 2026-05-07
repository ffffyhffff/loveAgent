<template>
  <div class="poi-selector glass-card">
    <h4>{{ title }}</h4>
    <div class="poi-list">
      <div v-for="(item, i) in items" :key="i"
           class="poi-item" :class="{ selected: selectedIndex === i }"
           @click="selectedIndex = i">
        <span class="poi-name">{{ item.name }}</span>
        <span class="poi-addr" v-if="item.address">{{ item.address }}</span>
        <span class="poi-dist" v-if="item.distance">{{ item.distance }}m</span>
      </div>
    </div>
    <button class="glass-btn primary" @click="confirm" :disabled="selectedIndex < 0">确认选择</button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
const props = defineProps({ title: String, items: Array })
const emit = defineEmits(['select'])
const selectedIndex = ref(-1)
const confirm = () => {
  if (selectedIndex.value >= 0) emit('select', props.items[selectedIndex.value])
}
</script>

<style scoped>
.poi-selector { padding: 16px; margin: 8px 0; }
h4 { font-size: 0.9rem; margin-bottom: 12px; }
.poi-list { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.poi-item {
  padding: 10px 14px; border-radius: 10px; cursor: pointer;
  background: var(--glass-bg); border: 1px solid var(--glass-border);
  transition: all 0.3s var(--transition-spring);
  display: flex; flex-direction: column; gap: 2px;
}
.poi-item:hover { background: var(--glass-bg-hover); }
.poi-item.selected { background: var(--color-accent-dim); border-color: var(--color-accent); }
.poi-name { font-size: 0.85rem; font-weight: 600; }
.poi-addr { font-size: 0.72rem; color: var(--color-text-secondary); }
.poi-dist { font-size: 0.7rem; color: var(--color-text-dim); }
</style>
