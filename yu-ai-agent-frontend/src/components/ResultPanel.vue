<template>
  <div class="result-panel">
    <div class="result-header">
      <div class="result-header-left">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" class="header-icon">
          <path d="M9 5H7C5.89543 5 5 5.89543 5 7V19C5 20.1046 5.89543 21 7 21H17C18.1046 21 19 20.1046 19 19V7C19 5.89543 18.1046 5 17 5H15M9 5C9 6.10457 9.89543 7 11 7H13C14.1046 7 15 6.10457 15 5M9 5C9 3.89543 9.89543 3 11 3H13C14.1046 3 15 3.89543 15 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <span class="result-title">执行计划</span>
        <span v-if="streaming" class="streaming-badge">运行中</span>
      </div>
      <button class="result-close" @click="$emit('close')" aria-label="关闭">✕</button>
    </div>

    <div class="result-body" ref="bodyRef">
      <!-- 步骤列表（带内嵌结果） -->
      <div v-for="(step, i) in steps" :key="'step-'+i"
           class="step-block" :class="step.status">
        <div class="step-head">
          <div class="step-indicator">
            <svg v-if="step.status === 'done'" width="12" height="12" viewBox="0 0 24 24" fill="none" class="step-icon-done">
              <path d="M5 13L9 17L19 7" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            <div v-else-if="step.status === 'active'" class="step-spinner"></div>
            <div v-else class="step-dot-pending"></div>
            <div v-if="i < steps.length - 1" class="step-connector" :class="step.status"></div>
          </div>
          <div class="step-info">
            <span class="step-label">{{ step.label }}</span>
            <span class="step-tool" v-if="getToolMeta(step.label)">{{ getToolMeta(step.label) }}</span>
          </div>
        </div>

        <!-- 该步骤关联的 POI 结果 -->
        <div v-if="getStepPois(i).length > 0" class="step-results">
          <div v-for="(poi, j) in getStepPois(i)" :key="j" class="step-poi-item">
            <div class="step-poi-left">
              <div class="step-poi-name">{{ poi.name }}</div>
              <div class="step-poi-addr" v-if="poi.address">{{ poi.address }}</div>
              <div class="step-poi-meta">
                <span v-if="poi.rating" class="meta-rating">★ {{ poi.rating }}</span>
                <span v-if="poi.distance" class="meta-dist">{{ poi.distance }}m</span>
                <span v-if="poi.cost" class="meta-cost">¥{{ poi.cost }}/人</span>
              </div>
            </div>
            <img v-if="poi.images && poi.images.length"
                 :src="poi.images[0]" class="step-poi-img" loading="lazy" />
          </div>
        </div>
      </div>

      <!-- 地图 -->
      <div v-if="selectedPois && selectedPois.length >= 2" class="map-section">
        <div class="section-header">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M9 20l-5-4V4l5 4 5-4 5 4v12l-5-4-5 4z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
          </svg>
          <span>路线规划</span>
          <span v-if="routeInfo" class="route-meta">{{ routeInfo.distance }} · {{ routeInfo.duration }}</span>
        </div>
        <RouteMap :pois="selectedPois" :routeInfo="routeInfo" />
      </div>

      <!-- PDF 内嵌预览 -->
      <div v-if="pdfUrl" class="pdf-section">
        <div class="section-header">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M14 2H6C4.89543 2 4 2.89543 4 4V20C4 21.1046 4.89543 22 6 22H18C19.1046 22 20 21.1046 20 20V8L14 2Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M14 2V8H20" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
          </svg>
          <span>约会计划书</span>
          <a :href="getFullPdfUrl(pdfUrl)" class="pdf-dl-btn" download>下载</a>
        </div>
        <iframe :src="getFullPdfUrl(pdfUrl)" class="pdf-iframe"
                frameborder="0" title="PDF 预览"></iframe>
      </div>

      <!-- 空态 -->
      <div v-if="!steps || steps.length === 0" class="empty-state">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" style="opacity:0.3;margin-bottom:12px">
          <path d="M12 8V12L15 15M21 12C21 16.9706 16.9706 21 12 21C7.02944 21 3 16.9706 3 12C3 7.02944 7.02944 3 12 3C16.9706 3 21 7.02944 21 12Z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <span>等待执行计划...</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import RouteMap from './RouteMap.vue'

const API_BASE = import.meta.env.PROD ? '/api' : 'http://localhost:8123/api'

const props = defineProps({
  steps: { type: Array, default: () => [] },
  pois: { type: Array, default: () => [] },
  selectedPois: { type: Array, default: () => [] },
  routeInfo: { type: Object, default: null },
  pdfUrl: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
})
defineEmits(['close'])

const bodyRef = ref(null)
watch(() => props.steps.length, () => nextTick(() => {
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}))
watch(() => props.pdfUrl, () => nextTick(() => {
  if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight
}))

// 从步骤标签推断使用了什么工具
const getToolMeta = (label) => {
  if (!label) return null
  if (label.includes('搜索') || label.includes('餐廳') || label.includes('餐厅') || label.includes('茶') || label.includes('咖啡') || label.includes('景点') || label.includes('甜品')) {
    const kw = label.replace(/^(完成[：:]?\s*|搜索|查找|寻找)/g, '').trim()
    return '高德地图搜索 · ' + (kw || 'POI')
  }
  if (label.includes('路线') || label.includes('规划')) return '高德地图 · 步行规划'
  if (label.includes('PDF') || label.includes('生成') || label.includes('細節') || label.includes('详情')) return 'PDF 生成 · 计划书'
  return null
}

// 从 POI 列表中智能分配到步骤
const getStepPois = (stepIndex) => {
  if (!props.pois || props.pois.length === 0) return []
  // 简单分段：将 POIs 均分到 steps
  const totalSteps = props.steps.length
  if (totalSteps === 0) return []
  const chunkSize = Math.ceil(props.pois.length / totalSteps)
  const start = stepIndex * chunkSize
  const end = Math.min(start + chunkSize, props.pois.length)
  return props.pois.slice(start, end)
}

const getFullPdfUrl = (path) => {
  if (!path) return '#'
  return API_BASE.replace('/api', '') + path
}
</script>

<style scoped>
.result-panel {
  display: flex; flex-direction: column;
  background: linear-gradient(180deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.02) 100%);
  border-left: 1px solid rgba(255,255,255,0.08);
  overflow: hidden;
  animation: slideIn 0.25s ease both;
}
@keyframes slideIn {
  from { opacity: 0; transform: translateX(12px); }
  to { opacity: 1; transform: translateX(0); }
}

/* Header */
.result-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
  backdrop-filter: blur(16px);
}
.result-header-left { display: flex; align-items: center; gap: 8px; }
.header-icon { color: #ff6b9d; flex-shrink: 0; }
.result-title { font-size: 0.88rem; font-weight: 700; letter-spacing: 0.01em; }
.streaming-badge {
  font-size: 0.6rem; padding: 2px 8px; border-radius: 10px;
  background: rgba(255,107,157,0.15); color: #ff6b9d;
  animation: pulse 1.8s infinite;
}
@keyframes pulse { 0%,100% { opacity: 1 } 50% { opacity: 0.5 } }
.result-close {
  background: none; border: none; color: #666; cursor: pointer;
  font-size: 16px; padding: 6px; border-radius: 6px; transition: all 0.2s;
}
.result-close:hover { color: #e91e63; background: rgba(233,30,99,0.08); }
.result-close:focus-visible { outline: 2px solid rgba(255,107,157,0.4); outline-offset: 2px; }

/* Body */
.result-body {
  flex: 1; overflow-y: auto; padding: 16px 18px;
  scroll-behavior: smooth;
}

/* Step Block */
.step-block { margin-bottom: 14px; }
.step-head { display: flex; gap: 12px; }
.step-indicator { display: flex; flex-direction: column; align-items: center; width: 24px; flex-shrink: 0; }
.step-icon-done { color: #4ecca3; margin: 3px 0; }
.step-spinner {
  width: 16px; height: 16px; border-radius: 50%; margin: 2px 0;
  border: 2px solid rgba(255,107,157,0.2); border-top-color: #ff6b9d;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.step-dot-pending {
  width: 10px; height: 10px; border-radius: 50%; margin: 5px 0;
  border: 2px solid rgba(255,255,255,0.15);
}
.step-connector { width: 2px; flex: 1; min-height: 16px; background: rgba(255,255,255,0.06); margin: 2px 0; }
.step-connector.done { background: rgba(76,204,163,0.3); }
.step-connector.active { background: rgba(255,107,157,0.3); }

.step-info { flex: 1; min-width: 0; padding-bottom: 4px; }
.step-label { font-size: 0.82rem; font-weight: 600; color: #e0e0e0; line-height: 1.5; }
.step-block.pending .step-label { color: #555; }
.step-block.done .step-label { color: #999; }
.step-tool {
  display: inline-block; margin-top: 3px; font-size: 0.65rem; color: #888;
  padding: 2px 6px; border-radius: 4px; background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.06);
}

/* Step POI Results */
.step-results { margin-left: 36px; margin-top: 8px; margin-bottom: 4px; }
.step-poi-item {
  display: flex; gap: 10px; padding: 10px 12px; margin-bottom: 6px;
  border-radius: 10px;
  background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06);
  transition: all 0.2s;
  cursor: pointer;
}
.step-poi-item:hover { background: rgba(255,255,255,0.06); border-color: rgba(255,255,255,0.1); }
.step-poi-left { flex: 1; min-width: 0; }
.step-poi-name { font-size: 0.8rem; font-weight: 600; color: #e0e0e0; margin-bottom: 3px; }
.step-poi-addr { font-size: 0.7rem; color: #888; line-height: 1.4; margin-bottom: 4px; }
.step-poi-meta { display: flex; gap: 8px; }
.meta-rating { font-size: 0.68rem; color: #f59e0b; font-weight: 500; }
.meta-dist { font-size: 0.68rem; color: #777; }
.meta-cost { font-size: 0.68rem; color: #4ecca3; }
.step-poi-img {
  width: 64px; height: 64px; object-fit: cover; border-radius: 8px;
  flex-shrink: 0; border: 1px solid rgba(255,255,255,0.06);
}

/* Section Header */
.section-header {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 0 8px; margin-top: 4px;
  font-size: 0.82rem; font-weight: 600; color: #ccc;
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.section-header svg { color: #ff6b9d; flex-shrink: 0; }
.route-meta { font-size: 0.7rem; color: #888; margin-left: auto; }

/* Map */
.map-section { margin-top: 14px; }

/* PDF */
.pdf-section { margin-top: 14px; }
.pdf-dl-btn {
  margin-left: auto; font-size: 0.72rem; padding: 4px 12px; border-radius: 6px;
  background: rgba(255,107,157,0.1); border: 1px solid rgba(255,107,157,0.2);
  color: #ff6b9d; text-decoration: none; cursor: pointer;
  transition: all 0.2s;
}
.pdf-dl-btn:hover { background: rgba(255,107,157,0.2); }
.pdf-iframe { width: 100%; height: 480px; border-radius: 10px; margin-top: 8px; border: 1px solid rgba(255,255,255,0.08); }

/* Empty */
.empty-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding: 60px 0; color: #555; font-size: 0.82rem;
}

@media (prefers-reduced-motion: reduce) {
  .result-panel { animation: none; }
  .step-spinner { animation: none; border-top-color: #ff6b9d; }
  .streaming-badge { animation: none; }
}
</style>
