<template>
  <div class="home">
    <div class="home-content">
      <!-- Logo -->
      <div class="hero" ref="heroRef">
        <div class="logo-ring">
          <div class="logo-icon">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none">
              <!-- 樱花五瓣 -->
              <circle cx="12" cy="6" r="3.5" fill="currentColor" opacity="0.9"/>
              <circle cx="18.3" cy="10" r="3.5" fill="currentColor" opacity="0.75"/>
              <circle cx="16" cy="17" r="3.5" fill="currentColor" opacity="0.85"/>
              <circle cx="8" cy="17" r="3.5" fill="currentColor" opacity="0.85"/>
              <circle cx="5.7" cy="10" r="3.5" fill="currentColor" opacity="0.75"/>
              <circle cx="12" cy="12" r="2.5" fill="#fff"/>
            </svg>
          </div>
        </div>
        <h1 class="title">AI 恋爱大师</h1>
        <p class="subtitle">倾听你的心声 · 温柔陪伴每一刻</p>
      </div>

      <!-- 功能卡片 -->
      <div class="features">
        <div class="feature-card glass-card" v-for="(f, i) in features" :key="i"
             :style="{ animationDelay: `${i * 0.12 + 0.3}s` }">
          <div class="feature-icon">{{ f.icon }}</div>
          <div class="feature-text">
            <h3>{{ f.title }}</h3>
            <p>{{ f.desc }}</p>
          </div>
        </div>
      </div>

      <!-- 开始按钮 -->
      <button class="start-btn glass-btn primary" @click="goChat" ref="btnRef">
        <span>开始对话</span>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
          <path d="M5 12H19M19 12L12 5M19 12L12 19" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>

      <!-- 底部装饰文字 -->
      <p class="footer-note">Powered by LangChain4j + DashScope</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { springAppear, springButtonEffect } from '../utils/spring'

const router = useRouter()
const heroRef = ref(null)
const btnRef = ref(null)

const features = [
  { icon: '🌸', title: '情感倾听', desc: '专业恋爱心理分析' },
  { icon: '💭', title: '心灵对话', desc: '温暖自然的 AI 交流' },
  { icon: '💕', title: '恋爱建议', desc: '实用的情感指导方案' },
]

const goChat = () => {
  router.push('/chat')
}

onMounted(() => {
  if (heroRef.value) springAppear(heroRef.value, 0)
  if (btnRef.value) {
    springAppear(btnRef.value, 600)
    springButtonEffect(btnRef.value)
  }
})
</script>

<style scoped>
.home {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 40px 20px;
}

.home-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 36px;
  max-width: 560px;
  width: 100%;
  z-index: 2;
}

.hero {
  text-align: center;
  opacity: 0;
}

.logo-ring {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.35);
  backdrop-filter: blur(20px);
  border: 2px solid rgba(255, 255, 255, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 28px;
  animation: floatSoft 5s ease-in-out infinite;
  box-shadow:
    0 8px 32px rgba(233, 30, 99, 0.15),
    0 0 0 6px rgba(255, 183, 197, 0.2);
}

.logo-icon {
  color: #e91e63;
}

.title {
  font-size: 2.8rem;
  font-weight: 800;
  background: linear-gradient(135deg, #e91e63, #f06292, #ec407a);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 12px;
  letter-spacing: 2px;
}

.subtitle {
  font-size: 1rem;
  color: var(--color-text-secondary);
  letter-spacing: 4px;
  font-weight: 300;
}

.features {
  display: flex;
  gap: 14px;
  width: 100%;
}

.feature-card {
  flex: 1;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  animation: springIn 0.6s var(--transition-spring) both;
  cursor: default;
  text-align: center;
}

.feature-icon {
  font-size: 2rem;
  line-height: 1;
}

.feature-text h3 {
  font-size: 0.9rem;
  font-weight: 600;
  margin-bottom: 3px;
}

.feature-text p {
  font-size: 0.75rem;
  color: var(--color-text-secondary);
  line-height: 1.4;
}

.start-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 44px;
  font-size: 16px;
  font-weight: 700;
  border-radius: 20px;
  opacity: 0;
  letter-spacing: 2px;
}

.start-btn svg {
  transition: transform 0.3s var(--transition-spring);
}

.start-btn:hover svg {
  transform: translateX(5px);
}

.footer-note {
  font-size: 0.7rem;
  color: var(--color-text-dim);
  letter-spacing: 1px;
  margin-top: 8px;
}

@media (max-width: 600px) {
  .title { font-size: 2.2rem; }
  .features { flex-direction: column; gap: 10px; }
  .feature-card { flex-direction: row; text-align: left; gap: 14px; }
}
</style>
