<template>
  <div class="result-panel">
    <div class="result-header">
      <span class="result-title">执行结果</span>
      <button class="result-close" @click="$emit('close')">✕</button>
    </div>

    <div class="result-tabs">
      <button v-for="tab in tabs" :key="tab.key"
        class="result-tab" :class="{ active: activeTab === tab.key }"
        @click="$emit('update:activeTab', tab.key)">
        {{ tab.icon }} {{ tab.label }}
      </button>
    </div>

    <div class="result-body">
      <!-- 计划 Tab -->
      <div v-show="activeTab === 'plan'" class="tab-content">
        <StepProgress v-if="steps && steps.length > 0" :steps="steps" />
        <div v-else class="empty-tab">等待执行计划...</div>
      </div>

      <!-- 工具 Tab -->
      <div v-show="activeTab === 'tools'" class="tab-content">
        <div v-if="pois && pois.length > 0" class="poi-grid">
          <PoiCard v-for="(poi, i) in pois" :key="i" :poi="poi" />
        </div>
        <div v-else class="empty-tab">暂无搜索结果</div>
      </div>

      <!-- 地图 Tab -->
      <div v-show="activeTab === 'map'" class="tab-content">
        <RouteMap v-if="selectedPois && selectedPois.length >= 2" :pois="selectedPois" :routeInfo="routeInfo" />
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

const API_BASE = import.meta.env.PROD ? '/api' : 'http://localhost:8123/api'

defineProps({
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
  animation: slideIn 0.3s ease 0.05s both;
}
@keyframes slideIn {
  from { opacity: 0; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}
.result-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; border-bottom: 1px solid rgba(255,255,255,0.1);
  flex-shrink: 0;
}
.result-title { font-size: 0.82rem; font-weight: 600; }
.result-close { background: none; border: none; color: #888; cursor: pointer; font-size: 14px; padding: 4px; border-radius: 4px; transition: color 0.2s; }
.result-close:hover { color: #e91e63; }
.result-tabs {
  display: flex; gap: 4px; padding: 8px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.06); flex-shrink: 0;
}
.result-tab {
  padding: 4px 10px; border-radius: 6px; font-size: 0.72rem;
  background: transparent; border: none; color: #888; cursor: pointer;
  transition: all 0.2s;
}
.result-tab.active { background: rgba(255,107,157,0.12); color: #ff6b9d; }
.result-tab:hover:not(.active) { color: #ccc; }
.result-body { flex: 1; overflow-y: auto; padding: 12px; }
.tab-content { min-height: 100%; }
.empty-tab { color: #555; font-size: 0.78rem; text-align: center; padding: 40px 0; }
.poi-grid { display: flex; flex-direction: column; gap: 8px; }
.pdf-card { display: flex; align-items: center; gap: 12px; padding: 14px; }
.pdf-icon { font-size: 24px; }
.pdf-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.pdf-title { font-size: 0.85rem; font-weight: 600; }
.pdf-desc { font-size: 0.75rem; color: #888; }
.pdf-btn { font-size: 13px; padding: 8px 16px; text-decoration: none; flex-shrink: 0; }
</style>
