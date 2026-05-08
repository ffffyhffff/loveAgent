<template>
  <Teleport to="body">
    <div class="dialog-overlay" @click.self="$emit('close')">
      <div class="dialog">
        <!-- 头部装饰 -->
        <div class="dialog-header">
          <div class="header-icon">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
              <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" fill="currentColor"/>
            </svg>
          </div>
          <h3 class="dialog-title">{{ formSpec.title || '补充信息' }}</h3>
          <p class="dialog-desc">请填写以下信息，帮助我为你制定更好的方案</p>
        </div>

        <!-- 表单内容 -->
        <div class="form-body">
          <div v-for="field in formSpec.fields" :key="field.key" class="form-group">
            <label class="field-label">
              <span class="label-icon">{{ getFieldIcon(field) }}</span>
              {{ field.label }}
              <span v-if="field.required" class="required">*</span>
            </label>

            <!-- 文本输入 -->
            <div v-if="field.type === 'text'" class="input-wrapper">
              <input
                v-model="answers[field.key]"
                :placeholder="field.placeholder || ''"
                class="form-input" />
            </div>

            <!-- 数字输入 -->
            <div v-else-if="field.type === 'number'" class="input-wrapper">
              <input
                type="number"
                v-model.number="answers[field.key]"
                :min="field.min"
                :max="field.max"
                :step="field.step || 1"
                :placeholder="field.placeholder || ''"
                class="form-input" />
            </div>

            <!-- 单选按钮组 -->
            <div v-else-if="field.type === 'radio'" class="option-group">
              <button v-for="opt in field.options" :key="opt"
                :class="{ active: answers[field.key] === opt }"
                @click="answers[field.key] = opt"
                class="option-btn">
                <span class="option-radio" :class="{ checked: answers[field.key] === opt }"></span>
                {{ opt }}
              </button>
            </div>

            <!-- 多选按钮组 -->
            <div v-else-if="field.type === 'checkbox'" class="option-group">
              <button v-for="opt in field.options" :key="opt"
                :class="{ active: isChecked(field.key, opt) }"
                @click="toggleCheckbox(field.key, opt)"
                class="option-btn">
                <span class="option-check" :class="{ checked: isChecked(field.key, opt) }">
                  <svg v-if="isChecked(field.key, opt)" width="12" height="12" viewBox="0 0 24 24" fill="none">
                    <path d="M5 13L9 17L19 7" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                  </svg>
                </span>
                {{ opt }}
              </button>
            </div>
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="dialog-actions">
          <button class="btn btn-cancel" @click="$emit('close')">取消</button>
          <button class="btn btn-submit" @click="submit" :disabled="!canSubmit">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M5 13L9 17L19 7" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            提交
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { reactive, computed } from 'vue'

const props = defineProps({
  formSpec: { type: Object, required: true },
})
const emit = defineEmits(['submit', 'close'])

// 初始化答案
const answers = reactive({})
if (props.formSpec.fields) {
  for (const field of props.formSpec.fields) {
    if (field.type === 'checkbox') {
      answers[field.key] = []
    } else {
      answers[field.key] = field.default || ''
    }
  }
}

const getFieldIcon = (field) => {
  const icons = {
    location: '📍', budget: '💰', style: '✨', duration: '⏱️',
    occasion: '🎯', activity: '🎭', keywords: '💡'
  }
  for (const [key, icon] of Object.entries(icons)) {
    if (field.key.toLowerCase().includes(key) || field.label.includes(key)) return icon
  }
  return '📝'
}

const isChecked = (key, opt) => Array.isArray(answers[key]) && answers[key].includes(opt)

const toggleCheckbox = (key, opt) => {
  if (!Array.isArray(answers[key])) answers[key] = []
  const idx = answers[key].indexOf(opt)
  if (idx >= 0) {
    answers[key].splice(idx, 1)
  } else {
    answers[key].push(opt)
  }
}

const canSubmit = computed(() => {
  if (!props.formSpec.fields) return false
  return props.formSpec.fields.every(field => {
    if (!field.required) return true
    const val = answers[field.key]
    if (Array.isArray(val)) return val.length > 0
    return val !== '' && val !== null && val !== undefined
  })
})

const submit = () => {
  if (!canSubmit.value) return
  const result = {}
  for (const field of props.formSpec.fields) {
    const val = answers[field.key]
    if (field.type === 'checkbox' && Array.isArray(val)) {
      result[field.key] = val.join(',')
    } else {
      result[field.key] = val
    }
  }
  emit('submit', { formId: props.formSpec.id, answers: result })
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed; inset: 0; z-index: 1000;
  background: rgba(0, 0, 0, 0.6); backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center;
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.dialog {
  width: 92%; max-width: 440px;
  background: linear-gradient(145deg, rgba(255,255,255,0.18), rgba(255,255,255,0.08));
  backdrop-filter: blur(30px); -webkit-backdrop-filter: blur(30px);
  border: 1px solid rgba(255,255,255,0.25);
  border-radius: 20px; overflow: hidden;
  box-shadow: 0 20px 60px rgba(0,0,0,0.3), 0 0 0 1px rgba(255,255,255,0.1) inset;
  animation: slideUp 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes slideUp {
  from { transform: translateY(30px) scale(0.97); opacity: 0; }
  to { transform: translateY(0) scale(1); opacity: 1; }
}

/* 头部 */
.dialog-header {
  padding: 24px 24px 16px;
  background: linear-gradient(135deg, rgba(139,92,246,0.15), rgba(236,72,153,0.1));
  border-bottom: 1px solid rgba(255,255,255,0.1);
  text-align: center;
}
.header-icon {
  width: 48px; height: 48px; margin: 0 auto 12px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(139,92,246,0.2), rgba(236,72,153,0.2));
  border: 1px solid rgba(255,255,255,0.2);
  display: flex; align-items: center; justify-content: center;
  color: #e91e63;
}
.dialog-title {
  font-size: 1.15rem; font-weight: 700; margin-bottom: 4px;
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
}
.dialog-desc { font-size: 0.82rem; color: rgba(255,255,255,0.6); }

/* 表单 */
.form-body { padding: 20px 24px; }

.form-group { margin-bottom: 18px; }
.form-group:last-child { margin-bottom: 0; }

.field-label {
  display: flex; align-items: center; gap: 6px;
  font-size: 0.85rem; font-weight: 600; margin-bottom: 8px;
  color: rgba(255,255,255,0.8);
}
.label-icon { font-size: 1rem; }
.required { color: #ec4899; margin-left: 2px; }

.input-wrapper { position: relative; }
.form-input {
  width: 100%; padding: 12px 16px; border-radius: 12px;
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: var(--color-text); font-size: 14px; outline: none;
  transition: all 0.25s; box-sizing: border-box;
}
.form-input:focus {
  border-color: rgba(139,92,246,0.5);
  background: rgba(255,255,255,0.12);
  box-shadow: 0 0 0 3px rgba(139,92,246,0.15);
}
.form-input::placeholder { color: rgba(255,255,255,0.35); }

/* 选项组 */
.option-group { display: flex; flex-wrap: wrap; gap: 8px; }
.option-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px; border-radius: 12px; font-size: 13px;
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.7); cursor: pointer;
  transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.option-btn:hover {
  background: rgba(255,255,255,0.12);
  border-color: rgba(255,255,255,0.2);
}
.option-btn.active {
  background: linear-gradient(135deg, rgba(139,92,246,0.2), rgba(236,72,153,0.15));
  border-color: rgba(139,92,246,0.4);
  color: #c084fc;
}

.option-radio {
  width: 16px; height: 16px; border-radius: 50%;
  border: 2px solid rgba(255,255,255,0.25); flex-shrink: 0;
  transition: all 0.2s;
}
.option-radio.checked {
  border-color: #8b5cf6;
  background: #8b5cf6;
  box-shadow: 0 0 0 3px rgba(139,92,246,0.2);
}

.option-check {
  width: 18px; height: 18px; border-radius: 5px;
  border: 2px solid rgba(255,255,255,0.25); flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.2s;
}
.option-check.checked {
  border-color: #8b5cf6;
  background: #8b5cf6;
  color: white;
}

/* 操作按钮 */
.dialog-actions {
  display: flex; gap: 10px; justify-content: flex-end;
  padding: 16px 24px 20px;
  border-top: 1px solid rgba(255,255,255,0.08);
}
.btn {
  display: flex; align-items: center; gap: 6px;
  padding: 10px 22px; border-radius: 12px; font-size: 14px; font-weight: 600;
  cursor: pointer; transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
.btn-cancel {
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.6);
}
.btn-cancel:hover {
  background: rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.8);
}
.btn-submit {
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
  border: none; color: white;
  box-shadow: 0 4px 15px rgba(139,92,246,0.3);
}
.btn-submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px rgba(139,92,246,0.4);
}
.btn-submit:active { transform: translateY(0); }
.btn-submit:disabled {
  opacity: 0.4; cursor: not-allowed;
  transform: none; box-shadow: none;
}
</style>
