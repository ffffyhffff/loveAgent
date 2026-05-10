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
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" class="tab-svg">
          <template v-if="tab.key === 'plan'">
            <path d="M9 5H7C5.89543 5 5 5.89543 5 7V19C5 20.1046 5.89543 21 7 21H17C18.1046 21 19 20.1046 19 19V7C19 5.89543 18.1046 5 17 5H15M9 5C9 6.10457 9.89543 7 11 7H13C14.1046 7 15 6.10457 15 5M9 5C9 3.89543 9.89543 3 11 3H13C14.1046 3 15 3.89543 15 5M12 12H15M12 16H15M9 12H9.01M9 16H9.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </template>
          <template v-else-if="tab.key === 'tools'">
            <path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
          </template>
          <template v-else-if="tab.key === 'map'">
            <path d="M9 20l-5-4V4l5 4 5-4 5 4v12l-5-4-5 4z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M9 4v16M15 4v16" stroke="currentColor" stroke-width="1.5"/>
          </template>
          <template v-else-if="tab.key === 'pdf'">
            <path d="M14 2H6C4.89543 2 4 2.89543 4 4V20C4 21.1046 4.89543 22 6 22H18C19.1046 22 20 21.1046 20 20V8L14 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M14 2V8H20" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M9 13H15M9 17H13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </template>
        </svg>
        {{ tab.label }}
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
          <svg width="28" height="28" viewBox="0 0 24 24" fill="none" class="pdf-icon-svg">
            <path d="M14 2H6C4.89543 2 4 2.89543 4 4V20C4 21.1046 4.89543 22 6 22H18C19.1046 22 20 21.1046 20 20V8L14 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M14 2V8H20" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M9 13H15M9 17H13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
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
  { key: 'plan', label: '计划' },
  { key: 'tools', label: '工具' },
  { key: 'map', label: '地图' },
  { key: 'pdf', label: 'PDF' },
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
.result-close:hover { color: #e91e63; background: rgba(233,30,99,0.08); }
.result-close:focus-visible { outline: 2px solid rgba(255,107,157,0.5); outline-offset: 2px; }
.result-tabs {
  display: flex; gap: 4px; padding: 8px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.06); flex-shrink: 0;
}
.result-tab {
  padding: 4px 10px; border-radius: 6px; font-size: 0.72rem;
  background: transparent; border: none; color: #888; cursor: pointer;
  transition: all 0.2s;
}
.result-tab {
  cursor: pointer; transition: all 0.2s ease;
}
.result-tab.active { background: rgba(255,107,157,0.12); color: #ff6b9d; }
.result-tab:hover:not(.active) { color: #ccc; background: rgba(255,255,255,0.04); }
.result-tab:focus-visible { outline: 2px solid rgba(255,107,157,0.5); outline-offset: -2px; border-radius: 6px; }
.tab-svg { flex-shrink: 0; opacity: 0.7; }
.result-tab.active .tab-svg { opacity: 1; }
.result-body { flex: 1; overflow-y: auto; padding: 12px; }
.tab-content { min-height: 100%; }
.empty-tab { color: #555; font-size: 0.78rem; text-align: center; padding: 40px 0; }
.poi-grid { display: flex; flex-direction: column; gap: 8px; }
.pdf-card { display: flex; align-items: center; gap: 12px; padding: 14px; }
.pdf-icon-svg { color: #ff6b9d; flex-shrink: 0; }
.pdf-icon { font-size: 24px; }
.pdf-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.pdf-title { font-size: 0.85rem; font-weight: 600; }
.pdf-desc { font-size: 0.75rem; color: #888; }
.pdf-btn { font-size: 13px; padding: 8px 16px; text-decoration: none; flex-shrink: 0; }

@media (prefers-reduced-motion: reduce) {
  .result-panel { animation: none; }
}
</style>
