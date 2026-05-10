<template>
  <div class="right-panel" :class="{ visible }">
    <div class="panel-header">
      <h3>约会规划</h3>
      <button class="close-btn" @click="$emit('close')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
          <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </button>
    </div>

    <div class="panel-body">
      <!-- 执行步骤 -->
      <div v-if="steps.length > 0" class="section">
        <StepProgress :steps="steps" />
      </div>

      <!-- POI 卡片列表 -->
      <div v-if="pois.length > 0" class="section">
        <h4 class="section-title">搜索到的地点</h4>
        <div class="poi-list">
          <div v-for="(poi, i) in pois" :key="i" class="poi-card">
            <div class="poi-card-name">{{ poi.name }}</div>
            <div class="poi-card-addr">{{ poi.address }}</div>
            <div v-if="poi.distance" class="poi-card-dist">{{ poi.distance }}m</div>
          </div>
        </div>
      </div>

      <!-- 地图 -->
      <div v-if="selectedPois.length >= 2" class="section map-section">
        <h4 class="section-title">路线规划</h4>
        <RouteMap :pois="selectedPois" :routeInfo="routeInfo" />
      </div>

      <!-- PDF 下载 -->
      <div v-if="pdfUrl" class="section pdf-section">
        <div class="pdf-card glass-card">
          <div class="pdf-icon">📄</div>
          <div class="pdf-info">
            <span class="pdf-title">约会计划书</span>
            <span class="pdf-desc">PDF 已生成</span>
          </div>
          <a :href="getFullPdfUrl(pdfUrl)" class="glass-btn primary pdf-btn" download>下载</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import StepProgress from './StepProgress.vue'
import RouteMap from './RouteMap.vue'

const API_BASE = process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8123/api'

defineProps({
  visible: { type: Boolean, default: false },
  steps: { type: Array, default: () => [] },
  pois: { type: Array, default: () => [] },
  selectedPois: { type: Array, default: () => [] },
  routeInfo: { type: Object, default: null },
  pdfUrl: { type: String, default: '' },
})

defineEmits(['close'])

const getFullPdfUrl = (path) => {
  if (!path) return '#'
  return API_BASE.replace('/api', '') + path
}
</script>

<style scoped>
.right-panel {
  width: 0; overflow: hidden; flex-shrink: 0;
  background: rgba(255,255,255,0.12); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px);
  border-left: 1px solid rgba(255,255,255,0.25);
  display: flex; flex-direction: column;
  transition: width 0.4s var(--transition-spring);
}
.right-panel.visible { width: 380px; }

.panel-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 16px; border-bottom: 1px solid rgba(255,255,255,0.2);
  flex-shrink: 0;
}
.panel-header h3 { font-size: 0.95rem; font-weight: 700; }
.close-btn {
  background: none; border: none; color: var(--color-text-dim);
  cursor: pointer; padding: 4px; border-radius: 6px;
  transition: all 0.2s;
}
.close-btn:hover { color: #e91e63; background: rgba(233,30,99,0.08); }

.panel-body {
  flex: 1; overflow-y: auto; padding: 16px;
  display: flex; flex-direction: column; gap: 24px;
}

.section-title {
  font-size: 0.82rem; font-weight: 600; color: var(--color-text-secondary);
  margin-bottom: 10px;
}

/* POI 卡片 */
.poi-list { display: flex; flex-direction: column; gap: 8px; }
.poi-card {
  padding: 10px 14px; border-radius: 10px;
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.12);
}
.poi-card-name { font-size: 0.85rem; font-weight: 600; }
.poi-card-addr { font-size: 0.75rem; color: var(--color-text-secondary); margin-top: 2px; }
.poi-card-dist { font-size: 0.72rem; color: var(--color-text-dim); margin-top: 2px; }

/* 地图区域 */
.map-section { min-height: 300px; }

/* PDF 区域 - 和地图分开 */
.pdf-section { margin-top: 8px; }

/* PDF 卡片 */
.pdf-card {
  display: flex; align-items: center; gap: 12px;
  padding: 14px;
}
.pdf-icon { font-size: 24px; }
.pdf-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.pdf-title { font-size: 0.85rem; font-weight: 600; }
.pdf-desc { font-size: 0.75rem; color: var(--color-text-secondary); }
.pdf-btn { font-size: 13px; padding: 8px 16px; text-decoration: none; flex-shrink: 0; }

/* 响应�?*/
@media (max-width: 768px) {
  .right-panel {
    position: fixed; bottom: -100%; left: 0; right: 0;
    width: 100% !important; height: 60vh; z-index: 50;
    border-left: none; border-top: 1px solid rgba(255,255,255,0.25);
    border-radius: 16px 16px 0 0;
    transition: bottom 0.4s var(--transition-spring);
  }
  .right-panel.visible { bottom: 0; }
}
</style>

