<template>
  <div class="poi-selector glass-card">
    <div class="selector-header">
      <h4>选择目的地</h4>
      <button class="close-btn" @click="$emit('close')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
          <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <div v-for="cat in categories" :key="cat.key" class="category-section">
      <div class="category-label">
        <span class="cat-icon">{{ getCatIcon(cat.label) }}</span>
        {{ cat.label }}
        <span class="cat-count">{{ cat.items.length }}个</span>
      </div>
      <div class="poi-list">
        <div v-for="(item, i) in cat.items" :key="i"
             class="poi-item" :class="{ selected: selections[cat.key] === i }"
             @click="selectPoi(cat.key, i)">
          <div class="poi-rank" :class="{ active: selections[cat.key] === i }">{{ i + 1 }}</div>
          <div class="poi-info">
            <span class="poi-name">{{ item.name }}</span>
            <span class="poi-addr" v-if="item.address">{{ item.address }}</span>
          </div>
          <span class="poi-dist" v-if="item.distance">{{ item.distance }}m</span>
        </div>
      </div>
    </div>

    <button class="confirm-btn" @click="confirm" :disabled="!canConfirm">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
        <path d="M5 13L9 17L19 7" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      确认修改
    </button>
  </div>
</template>

<script setup>
import { reactive, computed } from 'vue'

const props = defineProps({
  categories: { type: Array, default: () => [] },
  selected: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['confirm', 'close'])

// 初始化选择（每个类别默认选第一个）
const selections = reactive({ ...props.selected })

const canConfirm = computed(() => {
  return props.categories.some(cat => selections[cat.key] !== undefined)
})

function selectPoi(catKey, index) {
  selections[catKey] = index
}

function confirm() {
  // 收集所有选中的 POI
  const selectedPois = []
  for (const cat of props.categories) {
    const idx = selections[cat.key]
    if (idx !== undefined && cat.items[idx]) {
      selectedPois.push({
        ...cat.items[idx],
        label: cat.label,
        key: cat.key,
      })
    }
  }
  emit('confirm', selectedPois)
}

function getCatIcon(label) {
  if (label.includes('茶') || label.includes('咖啡') || label.includes('休闲')) return '☕'
  if (label.includes('景点') || label.includes('公园') || label.includes('观景')) return '🌸'
  if (label.includes('晚餐') || label.includes('餐厅') || label.includes('火锅') || label.includes('美食')) return '🍽️'
  if (label.includes('甜品') || label.includes('甜蜜')) return '🍰'
  if (label.includes('花店') || label.includes('浪漫') || label.includes('惊喜')) return '💐'
  if (label.includes('书店') || label.includes('文艺') || label.includes('展览')) return '📚'
  if (label.includes('酒吧') || label.includes('微醺')) return '🍷'
  if (label.includes('运动') || label.includes('体验') || label.includes('密室')) return '🎯'
  if (label.includes('小吃')) return '🍜'
  return '📍'
}
</script>

<style scoped>
.poi-selector {
  padding: 16px;
  margin: 8px 0;
  max-height: 70vh;
  overflow-y: auto;
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.selector-header h4 {
  font-size: 1rem;
  font-weight: 700;
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
}
.close-btn {
  background: none;
  border: none;
  color: var(--color-text-secondary);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: all 0.2s;
}
.close-btn:hover { background: rgba(255,255,255,0.1); }

.category-section { margin-bottom: 16px; }
.category-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.cat-icon { font-size: 1rem; }
.cat-count {
  font-size: 0.7rem;
  color: var(--color-text-dim);
  margin-left: auto;
}

.poi-list { display: flex; flex-direction: column; gap: 4px; }
.poi-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 10px;
  cursor: pointer;
  background: rgba(255,255,255,0.04);
  border: 1px solid transparent;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.poi-item:hover {
  background: rgba(255,255,255,0.08);
}
.poi-item.selected {
  background: linear-gradient(135deg, rgba(139,92,246,0.15), rgba(236,72,153,0.1));
  border-color: rgba(139,92,246,0.4);
}

.poi-rank {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  font-weight: 700;
  background: rgba(255,255,255,0.08);
  color: var(--color-text-dim);
  flex-shrink: 0;
  transition: all 0.2s;
}
.poi-rank.active {
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  color: white;
}

.poi-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.poi-name {
  font-size: 0.82rem;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.poi-addr {
  font-size: 0.7rem;
  color: var(--color-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.poi-dist {
  font-size: 0.68rem;
  color: var(--color-text-dim);
  flex-shrink: 0;
}

.confirm-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  border: none;
  color: white;
  cursor: pointer;
  margin-top: 8px;
  box-shadow: 0 4px 15px rgba(139,92,246,0.3);
  transition: all 0.25s;
}
.confirm-btn:hover { transform: translateY(-1px); box-shadow: 0 6px 20px rgba(139,92,246,0.4); }
.confirm-btn:disabled { opacity: 0.4; cursor: not-allowed; transform: none; box-shadow: none; }
</style>
