<template>
  <div class="bubble-row" :class="{ 'user-row': msg.isUser }">
    <!-- 普通文本消息 -->
    <template v-if="!msg.type || msg.type === 'text'">
      <div v-if="!msg.isUser" class="avatar ai-avatar">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="6" r="3" fill="currentColor"/>
          <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
          <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
        </svg>
      </div>
      <div class="bubble" :class="{ user: msg.isUser, ai: !msg.isUser }">
        <div class="bubble-content">{{ msg.content }}</div>
      </div>
      <div v-if="msg.isUser" class="avatar user-avatar"><span>我</span></div>
    </template>

    <!-- PDF 下载卡片 -->
    <template v-else-if="msg.type === 'pdf'">
      <div class="avatar ai-avatar">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="6" r="3" fill="currentColor"/>
          <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
          <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
        </svg>
      </div>
      <div class="pdf-card glass-card">
        <div class="pdf-icon">📄</div>
        <div class="pdf-info">
          <span class="pdf-title">约会计划书</span>
          <span class="pdf-desc">PDF 已生成，点击下载</span>
        </div>
        <a :href="getPdfUrl(msg.url)" class="glass-btn primary pdf-btn" download>下载</a>
      </div>
    </template>

    <!-- 执行面板 -->
    <template v-else-if="msg.type === 'execution' && msg.execution">
      <div class="avatar ai-avatar">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="6" r="3" fill="currentColor"/>
          <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
          <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
        </svg>
      </div>
      <div class="exec-panel glass-card">
        <div class="exec-header">执行计划</div>
        <div v-for="(step, i) in msg.execution.steps" :key="'s'+i" class="exec-step" :class="step.status">
          <span v-if="step.status === 'done'" class="step-icon">✅</span>
          <span v-else-if="step.status === 'active'" class="step-icon spinning">⏳</span>
          <span v-else class="step-icon">○</span>
          <span class="step-text">{{ step.text }}</span>
        </div>
        <div v-for="(result, i) in msg.execution.results" :key="'r'+i" class="exec-result">
          <div class="exec-result-header">{{ result.icon }} {{ result.label }}</div>
          <div v-for="(poi, j) in result.items" :key="j" class="exec-poi">
            <span class="poi-name">{{ poi.name }}</span>
            <span class="poi-meta"><span v-if="poi.distance">{{ poi.distance }}m</span></span>
          </div>
        </div>
        <div v-for="(detail, i) in msg.execution.details" :key="'d'+i" class="exec-detail">
          <div class="detail-header">📷 {{ detail.name }}</div>
          <div v-if="detail.text" class="detail-info">{{ detail.text }}</div>
          <div v-if="detail.images && detail.images.length" class="detail-images">
            <img v-for="(img, k) in detail.images.slice(0,3)" :key="k" :src="img" class="detail-img" loading="lazy" />
          </div>
        </div>
      </div>
    </template>

    <!-- 地图路线 -->
    <template v-else-if="msg.type === 'map'">
      <div class="avatar ai-avatar">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="6" r="3" fill="currentColor"/>
          <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
          <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
        </svg>
      </div>
      <div class="map-bubble-wrapper">
        <RouteMap :pois="msg.pois" :routeInfo="msg.routeInfo" />
        <div class="map-actions">
          <button class="action-btn" @click="$emit('changePoi', msg)">换目的地</button>
          <button class="action-btn" @click="$emit('aiModify', msg)">让 AI 改</button>
        </div>
      </div>
    </template>

    <!-- LoveAgent 动态表单占位 -->
    <template v-else-if="msg.type === 'form'">
      <div class="avatar ai-avatar">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
          <circle cx="12" cy="6" r="3" fill="currentColor"/>
          <circle cx="17" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
          <circle cx="15.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="8.5" cy="15.5" r="3" fill="currentColor" opacity="0.8"/>
          <circle cx="7" cy="9.5" r="3" fill="currentColor" opacity="0.7"/>
        </svg>
      </div>
      <div class="bubble ai form-placeholder-bubble">
        <span class="form-waiting-text">📋 {{ msg.formSpec?.title || '请填写信息' }} - 弹窗已打开</span>
      </div>
    </template>

    <!-- 其他类型：简单回退气泡 -->
    <template v-else>
      <div class="bubble" :class="{ user: msg.isUser, ai: !msg.isUser }">
        <div class="bubble-content">{{ msg.content }}</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import RouteMap from './RouteMap.vue'

defineProps({ msg: { type: Object, required: true } })
defineEmits(['changePoi', 'aiModify'])

const API_BASE = import.meta.env.PROD ? '/api' : 'http://localhost:8123/api'
const getPdfUrl = (path) => path ? API_BASE.replace('/api', '') + path : '#'
</script>

<style scoped>
.bubble-row { display: flex; align-items: flex-end; gap: 10px; max-width: 85%; animation: springIn 0.5s var(--transition-spring) both; }
.bubble-row.user-row { align-self: flex-end; }
.avatar { width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ai-avatar { background: rgba(233,30,99,0.08); color: #e91e63; border: 1px solid rgba(233,30,99,0.12); }
.user-avatar { background: rgba(233,30,99,0.12); color: #e91e63; font-size: 11px; font-weight: 700; border: 1px solid rgba(233,30,99,0.15); }
.bubble { padding: 11px 15px; border-radius: 16px; line-height: 1.6; font-size: 14px; word-break: break-word; }
.bubble.ai { background: rgba(255,255,255,0.5); border: 1px solid rgba(255,255,255,0.45); border-bottom-left-radius: 4px; }
.bubble.user { background: rgba(233,30,99,0.1); border: 1px solid rgba(233,30,99,0.12); border-bottom-right-radius: 4px; }
.bubble-content { white-space: pre-wrap; }

/* PDF 卡片 */
.pdf-card {
  display: flex; align-items: center; gap: 14px;
  padding: 16px; margin: 4px 0;
}
.pdf-icon { font-size: 28px; }
.pdf-info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.pdf-title { font-size: 0.9rem; font-weight: 600; }
.pdf-desc { font-size: 0.78rem; color: var(--color-text-secondary); }
.pdf-btn { font-size: 13px; padding: 8px 18px; text-decoration: none; }

/* LoveAgent 表单占位气泡 */
.form-placeholder-bubble { display: flex; align-items: center; }
.form-waiting-text { font-size: 0.82rem; color: var(--color-text-secondary); font-style: italic; }

/* 执行面板 */
.exec-panel { padding: 14px 16px; margin: 8px 0; max-width: 600px; }
.exec-header {
  font-size: 0.82rem; font-weight: 700; margin-bottom: 10px; padding-bottom: 6px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
}
.exec-step { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 0.78rem; }
.exec-step.done { color: var(--color-text-secondary); }
.exec-step.active { color: #f59e0b; font-weight: 600; }
.exec-step.pending { color: var(--color-text-dim); }
.step-icon { flex-shrink: 0; width: 18px; text-align: center; }
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
.exec-result { margin-top: 10px; padding: 8px 10px; background: rgba(255,255,255,0.04); border-radius: 10px; }
.exec-result-header { font-size: 0.8rem; font-weight: 600; margin-bottom: 6px; }
.exec-poi { display: flex; justify-content: space-between; align-items: center; padding: 3px 0; font-size: 0.75rem; }
.exec-poi .poi-name { color: var(--color-text); }
.exec-poi .poi-meta { color: var(--color-text-dim); font-size: 0.68rem; }
.exec-detail { margin-top: 10px; padding: 8px 10px;
  background: rgba(139,92,246,0.06); border-radius: 10px; border: 1px solid rgba(139,92,246,0.1); }
.detail-header { font-size: 0.8rem; font-weight: 600; margin-bottom: 4px; }
.detail-info { font-size: 0.72rem; color: var(--color-text-secondary); white-space: pre-wrap; margin-bottom: 6px; }
.detail-images { display: flex; gap: 6px; }
.detail-img { width: 100px; height: 75px; object-fit: cover; border-radius: 6px; }

/* 地图 */
.map-bubble-wrapper { width: 100%; max-width: 600px; border-radius: 16px; overflow: hidden; }
.map-actions { display: flex; gap: 8px; margin-top: 10px; padding: 0 4px; }
.action-btn {
  padding: 8px 14px; border-radius: 10px; font-size: 0.78rem; font-weight: 600;
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: var(--color-text-secondary); cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.action-btn:hover {
  background: linear-gradient(135deg, rgba(139,92,246,0.15), rgba(236,72,153,0.1));
  border-color: rgba(139,92,246,0.3); color: #c084fc;
}
</style>
