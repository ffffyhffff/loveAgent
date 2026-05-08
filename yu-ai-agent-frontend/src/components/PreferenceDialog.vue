<template>
  <Teleport to="body">
    <div class="dialog-overlay" @click.self="$emit('close')">
      <div class="dialog glass-card">
        <h3 class="dialog-title">补充约会信息</h3>
        <p class="dialog-desc">请补充以下信息，帮我生成更精准的约会方案</p>

        <div class="form-group">
          <label>📍 约会地点</label>
          <input v-model="form.location" placeholder="如：北京朝阳区、杭州西湖" />
        </div>

        <div class="form-group">
          <label>💰 预算范围</label>
          <input v-model="form.budget" placeholder="如：300-500元" />
        </div>

        <div class="form-group">
          <label>✨ 约会风格</label>
          <div class="style-options">
            <button v-for="s in styles" :key="s"
                    :class="{ active: form.style === s }"
                    @click="form.style = s">{{ s }}</button>
          </div>
        </div>

        <div class="form-group">
          <label>🎯 约会场景</label>
          <div class="style-options">
            <button v-for="o in occasions" :key="o"
                    :class="{ active: form.occasion === o }"
                    @click="form.occasion = o">{{ o }}</button>
          </div>
        </div>

        <div class="form-group">
          <label>🎭 活动偏好</label>
          <div class="style-options">
            <button v-for="a in activities" :key="a"
                    :class="{ active: form.activity === a }"
                    @click="form.activity = a">{{ a }}</button>
          </div>
        </div>

        <div class="form-group">
          <label>⏱️ 时长</label>
          <div class="style-options">
            <button v-for="d in durations" :key="d"
                    :class="{ active: form.duration === d }"
                    @click="form.duration = d">{{ d }}</button>
          </div>
        </div>

        <div class="form-group">
          <label>💡 特殊要求（可选）</label>
          <input v-model="form.keywords" placeholder="如：对方喜欢安静、想拍照出片" />
        </div>

        <div class="dialog-actions">
          <button class="glass-btn" @click="$emit('close')">取消</button>
          <button class="glass-btn primary" @click="submit" :disabled="!form.location">
            生成计划
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { reactive } from 'vue'

const props = defineProps({
  initial: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['submit', 'close'])

const styles = ['浪漫', '休闲', '冒险', '文艺', '美食']
const occasions = ['普通约会', '纪念日', '生日', '求婚', '第一次约会']
const activities = ['放松休闲', '动感体验', '文艺探索', '纯吃为主']
const durations = ['半天', '一天', '晚上']

const form = reactive({
  location: props.initial.location || '',
  budget: props.initial.budget || '',
  style: props.initial.style || '浪漫',
  occasion: props.initial.occasion || '普通约会',
  activity: props.initial.activity || '放松休闲',
  duration: props.initial.duration || '半天',
  keywords: props.initial.keywords || '',
})

const submit = () => {
  if (!form.location) return
  emit('submit', { ...form })
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0,0,0,0.5); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  animation: fadeIn 0.2s ease;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.dialog {
  width: 90%; max-width: 420px; padding: 28px;
  animation: slideUp 0.3s var(--transition-spring);
}
@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.dialog-title { font-size: 1.1rem; font-weight: 700; margin-bottom: 6px; }
.dialog-desc { font-size: 0.82rem; color: var(--color-text-secondary); margin-bottom: 20px; }

.form-group { margin-bottom: 16px; }
.form-group label {
  display: block; font-size: 0.82rem; font-weight: 600;
  margin-bottom: 6px; color: var(--color-text-secondary);
}
.form-group input {
  width: 100%; padding: 10px 14px; border-radius: 10px;
  background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.2);
  color: var(--color-text); font-size: 14px; outline: none;
  transition: border-color 0.2s;
}
.form-group input:focus { border-color: rgba(233,30,99,0.4); }
.form-group input::placeholder { color: var(--color-text-dim); }

.style-options { display: flex; flex-wrap: wrap; gap: 8px; }
.style-options button {
  padding: 6px 14px; border-radius: 20px; font-size: 13px;
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: var(--color-text-secondary); cursor: pointer;
  transition: all 0.2s var(--transition-spring);
}
.style-options button:hover { background: rgba(255,255,255,0.15); }
.style-options button.active {
  background: rgba(233,30,99,0.12); border-color: rgba(233,30,99,0.3);
  color: #e91e63;
}

.dialog-actions {
  display: flex; gap: 10px; justify-content: flex-end; margin-top: 24px;
}
.dialog-actions button { font-size: 13px; padding: 8px 20px; }
.dialog-actions .primary { font-weight: 600; }
</style>
