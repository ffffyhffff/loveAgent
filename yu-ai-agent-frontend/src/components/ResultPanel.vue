<template>
  <div class="rp">
    <div class="rp-hd">
      <span class="rp-title">执行结果</span>
      <div class="rp-hd-right">
        <span v-if="streaming" class="rp-badge">运行中</span>
        <button class="rp-close" @click="$emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
        </button>
      </div>
    </div>

    <!-- Tabs -->
    <div class="rp-tabs">
      <button v-for="t in tabs" :key="t.key" class="rp-tab" :class="{ on: activeTab === t.key }"
              @click="$emit('update:activeTab', t.key)">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" class="rp-tab-svg">
          <template v-if="t.key === 'plan'">
            <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M12 12h3M12 16h3M9 12h.01M9 16h.01" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </template>
          <template v-else-if="t.key === 'tools'">
            <path d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.72 1.72 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.72 1.72 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.72 1.72 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.72 1.72 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.72 1.72 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.72 1.72 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.72 1.72 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
            <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.5"/>
          </template>
          <template v-else-if="t.key === 'map'">
            <path d="M9 20l-5-4V4l5 4 5-4 5 4v12l-5-4-5 4zM9 4v16M15 4v16" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
          </template>
          <template v-else>
            <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6zM14 2v6h6" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
          </template>
        </svg>
        {{ t.label }}
      </button>
    </div>

    <div class="rp-body">
      <!-- ====== 计划 Tab ====== -->
      <div v-show="activeTab === 'plan'" class="rp-tc">
        <div v-if="steps.length" class="plan-wrap">
          <div v-for="(s, i) in steps" :key="i" class="plan-row" :class="s.status">
            <div class="plan-dot-col">
              <div class="plan-dot" :class="s.status">
                <svg v-if="s.status === 'done'" width="12" height="12" viewBox="0 0 24 24" fill="none"><path d="M5 13l4 4 7-10" stroke="#4ecca3" stroke-width="3" stroke-linecap="round"/></svg>
                <div v-else-if="s.status === 'active'" class="plan-dot-ring"></div>
              </div>
              <div v-if="i < steps.length - 1" class="plan-line" :class="s.status"></div>
            </div>
            <div class="plan-body">
              <span class="plan-label">{{ cleanLabel(s.label) }}</span>
              <span class="plan-tool">{{ getToolName(s.label) }}</span>
            </div>
          </div>
        </div>
        <div v-else class="rp-empty">等待执行计划...</div>
      </div>

      <!-- ====== 工具 Tab ====== -->
      <div v-show="activeTab === 'tools'" class="rp-tc">
        <div v-if="toolCalls.length" class="tc-list">
          <div v-for="(tc, i) in toolCalls" :key="i" class="tc-card">
            <!-- 工具调用头部 -->
            <div class="tc-head">
              <div class="tc-head-left">
                <div class="tc-icon" :class="tc.status">
                  <svg v-if="tc.status === 'done'" width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M5 13l4 4 7-10" stroke="#4ecca3" stroke-width="2.5" stroke-linecap="round"/></svg>
                  <div v-else class="tc-spin"></div>
                </div>
                <div>
                  <div class="tc-tool-name">{{ tc.toolName }}</div>
                  <div class="tc-tool-input">{{ tc.toolInput }}</div>
                </div>
              </div>
              <span class="tc-time" v-if="tc.startTime && tc.endTime">{{ ((tc.endTime - tc.startTime) / 1000).toFixed(1) }}s</span>
              <span class="tc-time" v-else-if="tc.status === 'running'">执行中...</span>
            </div>
            <!-- 工具调用结果 -->
            <div v-if="tc.results.length" class="tc-results">
              <div v-for="(r, j) in tc.results" :key="j" class="tc-poi">
                <img v-if="r.images && r.images.length" :src="r.images[0]" class="tc-poi-img" loading="lazy" />
                <div class="tc-poi-info">
                  <div class="tc-poi-name">{{ r.name }}</div>
                  <div class="tc-poi-addr" v-if="r.address">{{ r.address }}</div>
                  <div class="tc-poi-meta">
                    <span v-if="r.rating" class="m-rating">★ {{ r.rating }}</span>
                    <span v-if="r.distance" class="m-dist">{{ r.distance }}m</span>
                    <span v-if="r.cost" class="m-cost">¥{{ r.cost }}/人</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-else-if="tc.status === 'running'" class="tc-wait">等待结果...</div>
          </div>
        </div>
        <div v-else class="rp-empty">暂无工具调用记录</div>
      </div>

      <!-- ====== 地图 Tab ====== -->
      <div v-show="activeTab === 'map'" class="rp-tc">
        <RouteMap v-if="selectedPois.length >= 2" :pois="selectedPois" :routeInfo="routeInfo" />
        <div v-else class="rp-empty">等待路线规划...</div>
      </div>

      <!-- ====== PDF Tab ====== -->
      <div v-show="activeTab === 'pdf'" class="rp-tc">
        <div v-if="pdfUrl">
          <iframe :src="pdfFull(pdfUrl)" class="pdf-frame" frameborder="0" title="PDF"></iframe>
          <a :href="pdfFull(pdfUrl)" class="pdf-dl" download>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none"><path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M7 10l5 5 5-5M12 15V3" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            下载 PDF
          </a>
        </div>
        <div v-else class="rp-empty">PDF 尚未生成</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import RouteMap from './RouteMap.vue'

const API = import.meta.env.PROD ? '/api' : 'http://localhost:8123/api'

defineProps({
  activeTab: { type: String, default: 'plan' },
  steps: { type: Array, default: () => [] },
  toolCalls: { type: Array, default: () => [] },
  pois: { type: Array, default: () => [] },
  selectedPois: { type: Array, default: () => [] },
  routeInfo: { type: Object, default: null },
  pdfUrl: { type: String, default: '' },
  streaming: { type: Boolean, default: false },
})
defineEmits(['update:activeTab', 'close'])

const tabs = [
  { key: 'plan', label: '计划' },
  { key: 'tools', label: '工具' },
  { key: 'map', label: '地图' },
  { key: 'pdf', label: 'PDF' },
]

const cleanLabel = (l) => (l || '').replace(/^完成[：:]\s*/, '')
const getToolName = (l) => {
  if (!l) return ''
  const m = (l || '').replace(/^完成[：:]\s*/, '')
  if (/搜索|查找/.test(m)) return '高德POI搜索'
  if (/路线|步行|规划/.test(m)) return '步行路线规划'
  if (/详情|补充|图片/.test(m)) return '详情获取'
  if (/PDF|生成|计划书/.test(m)) return 'PDF 生成'
  return ''
}
const pdfFull = (p) => p ? API.replace('/api', '') + p : '#'
</script>

<style scoped>
/* ====== PANEL ====== */
.rp { display:flex; flex-direction:column; min-width:340px; background:rgba(255,255,255,0.04); border-left:1px solid rgba(255,255,255,0.08); backdrop-filter:blur(20px); -webkit-backdrop-filter:blur(20px); }

/* ====== HEADER ====== */
.rp-hd { display:flex; align-items:center; padding:14px 18px; border-bottom:1px solid rgba(255,255,255,0.08); gap:10px; }
.rp-title { font-size:0.92rem; font-weight:700; color:#f0f0f0; flex:1; }
.rp-hd-right { display:flex; align-items:center; gap:8px; }
.rp-badge { font-size:0.6rem; padding:3px 10px; border-radius:10px; background:rgba(255,107,157,0.15); color:#ff6b9d; animation:pulse 1.8s infinite; }
@keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.5} }
.rp-close { background:none; border:none; color:rgba(255,255,255,0.35); cursor:pointer; padding:6px; border-radius:8px; transition:all 0.2s; }
.rp-close:hover { color:#ff6b9d; background:rgba(255,107,157,0.08); }

/* ====== TABS ====== */
.rp-tabs { display:flex; gap:2px; padding:8px 14px; border-bottom:1px solid rgba(255,255,255,0.06); }
.rp-tab { display:flex; align-items:center; gap:5px; padding:7px 14px; border-radius:8px; font-size:0.78rem; background:transparent; border:none; color:rgba(255,255,255,0.40); cursor:pointer; transition:all 0.2s; font-weight:500; }
.rp-tab:hover { color:rgba(255,255,255,0.65); background:rgba(255,255,255,0.03); }
.rp-tab.on { background:rgba(255,107,157,0.10); color:#ff6b9d; }
.rp-tab-svg { opacity:0.45; flex-shrink:0; }
.rp-tab.on .rp-tab-svg { opacity:1; }
.rp-tab:hover .rp-tab-svg { opacity:0.7; }

/* ====== BODY ====== */
.rp-body { flex:1; overflow-y:auto; padding:16px; }
.rp-tc { min-height:100%; }
.rp-empty { color:rgba(255,255,255,0.20); font-size:0.82rem; text-align:center; padding:60px 0; }

/* ====== 计划 Tab ====== */
.plan-wrap { padding-left:4px; }
.plan-row { display:flex; gap:12px; position:relative; padding-bottom:22px; }
.plan-row:last-child { padding-bottom:0; }
.plan-dot-col { display:flex; flex-direction:column; align-items:center; flex-shrink:0; }
.plan-dot { width:26px; height:26px; border-radius:50%; display:flex; align-items:center; justify-content:center; }
.plan-dot.done { background:rgba(76,204,163,0.12); }
.plan-dot.active { background:rgba(255,107,157,0.12); }
.plan-dot.pending { border:2px solid rgba(255,255,255,0.08); }
.plan-dot-ring { width:12px; height:12px; border-radius:50%; border:2px solid rgba(255,107,157,0.2); border-top-color:#ff6b9d; animation:spin 0.8s linear infinite; }
@keyframes spin { to { transform:rotate(360deg) } }
.plan-line { width:2px; flex:1; min-height:16px; margin:4px 0; background:rgba(255,255,255,0.04); }
.plan-line.done { background:rgba(76,204,163,0.25); }
.plan-line.active { background:rgba(255,107,157,0.2); }
.plan-body { flex:1; display:flex; flex-direction:column; gap:3px; padding-top:2px; }
.plan-label { font-size:0.84rem; font-weight:600; color:#dadada; line-height:1.4; }
.plan-row.pending .plan-label { color:rgba(255,255,255,0.22); }
.plan-row.done .plan-label { color:rgba(255,255,255,0.50); }
.plan-tool { font-size:0.62rem; color:rgba(255,255,255,0.30); padding:1px 8px; border-radius:4px; background:rgba(255,255,255,0.03); width:fit-content; }

/* ====== 工具 Tab ====== */
.tc-list { display:flex; flex-direction:column; gap:14px; }
.tc-card { border-radius:14px; background:rgba(255,255,255,0.04); border:1px solid rgba(255,255,255,0.07); overflow:hidden; }
.tc-head { display:flex; align-items:center; gap:10px; padding:14px 16px; border-bottom:1px solid rgba(255,255,255,0.04); }
.tc-head-left { display:flex; align-items:center; gap:10px; flex:1; min-width:0; }
.tc-icon { width:32px; height:32px; border-radius:10px; display:flex; align-items:center; justify-content:center; flex-shrink:0; }
.tc-icon.done { background:rgba(76,204,163,0.12); }
.tc-icon.running { background:rgba(255,107,157,0.12); }
.tc-spin { width:14px; height:14px; border-radius:50%; border:2px solid rgba(255,107,157,0.15); border-top-color:#ff6b9d; animation:spin 0.8s linear infinite; }
.tc-tool-name { font-size:0.82rem; font-weight:700; color:#eaeaea; }
.tc-tool-input { font-size:0.68rem; color:rgba(255,255,255,0.38); margin-top:2px; }
.tc-time { font-size:0.65rem; color:rgba(255,255,255,0.30); flex-shrink:0; }
.tc-results { padding:10px 16px 14px; display:flex; flex-direction:column; gap:8px; }
.tc-wait { padding:14px 16px; font-size:0.72rem; color:rgba(255,255,255,0.22); text-align:center; }
.tc-poi { display:flex; gap:10px; padding:10px; border-radius:10px; background:rgba(255,255,255,0.02); border:1px solid rgba(255,255,255,0.04); cursor:pointer; transition:all 0.18s; }
.tc-poi:hover { background:rgba(255,255,255,0.05); border-color:rgba(255,255,255,0.08); }
.tc-poi-img { width:56px; height:56px; object-fit:cover; border-radius:8px; flex-shrink:0; border:1px solid rgba(255,255,255,0.05); }
.tc-poi-info { flex:1; min-width:0; display:flex; flex-direction:column; gap:3px; }
.tc-poi-name { font-size:0.80rem; font-weight:600; color:#eaeaea; }
.tc-poi-addr { font-size:0.68rem; color:rgba(255,255,255,0.40); line-height:1.35; }
.tc-poi-meta { display:flex; gap:8px; margin-top:2px; }
.m-rating { font-size:0.66rem; color:#f59e0b; font-weight:500; }
.m-dist { font-size:0.66rem; color:rgba(255,255,255,0.35); }
.m-cost { font-size:0.66rem; color:#4ecca3; }

/* ====== PDF Tab ====== */
.pdf-frame { width:100%; min-height:520px; border-radius:10px; border:1px solid rgba(255,255,255,0.08); background:#f5f5f5; }
.pdf-dl { display:flex; align-items:center; justify-content:center; gap:6px; margin-top:10px; padding:10px; border-radius:10px; font-size:0.80rem; font-weight:600; background:rgba(255,107,157,0.08); border:1px solid rgba(255,107,157,0.15); color:#ff6b9d; text-decoration:none; cursor:pointer; transition:all 0.2s; }
.pdf-dl:hover { background:rgba(255,107,157,0.15); }

@media (prefers-reduced-motion:reduce) { .tc-spin,.plan-dot-ring { animation:none; } .rp-badge { animation:none; } }
</style>
