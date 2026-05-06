<template>
  <div class="home">
    <div class="home-content">
      <!-- 顶部导航 -->
      <nav class="top-nav">
        <div class="nav-logo">
          <div class="logo-dot"></div>
          <span>LoveAgent</span>
        </div>
        <button class="glass-btn primary nav-btn" @click="goChat">
          开始使用
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
            <path d="M5 12H19M19 12L12 5M19 12L12 19" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
          </svg>
        </button>
      </nav>

      <!-- Hero 区域 -->
      <section class="hero">
        <div class="hero-badge">
          <span class="badge-dot"></span>
          Powered by LangChain4j + pgvector
        </div>
        <h1 class="hero-title">
          <span class="gradient-text">AI 恋爱大师</span>
        </h1>
        <p class="hero-subtitle">Reimagine Your Love Life With AI</p>
        <p class="hero-desc">
          56个专业恋爱知识库 · 混合检索 + Rerank精排 · 多轮对话记忆
        </p>
        <div class="hero-actions">
          <button class="glass-btn primary hero-cta" @click="goChat">
            开始对话
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
              <path d="M5 12H19M19 12L12 5M19 12L12 19" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
          <a href="https://github.com/ffffyhffff/loveAgent" target="_blank" class="glass-btn github-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/>
            </svg>
            GitHub
          </a>
        </div>
      </section>

      <!-- 功能展示 -->
      <section class="features">
        <div class="feature-card" v-for="(f, i) in features" :key="i"
             :style="{ animationDelay: `${i * 0.1 + 0.3}s` }">
          <div class="feature-icon">{{ f.icon }}</div>
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
        </div>
      </section>

      <!-- 技术栈 -->
      <section class="tech-section">
        <h2 class="section-title">技术栈</h2>
        <div class="tech-tags">
          <span class="tech-tag" v-for="t in techs" :key="t">{{ t }}</span>
        </div>
      </section>

      <!-- 底部 -->
      <footer class="home-footer">
        <p>LoveAgent · Built with LangChain4j + pgvector + Vue 3</p>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const features = [
  { icon: '🧠', title: 'RAG 知识库', desc: '56个专业恋爱Q&A，混合检索+Rerank精排' },
  { icon: '💬', title: '多轮对话', desc: 'PostgreSQL持久化，对话历史永不丢失' },
  { icon: '📡', title: '流式输出', desc: 'SSE实时推送，打字机般的回复体验' },
  { icon: '🛠️', title: 'Agent工具', desc: '搜索、抓取、文件操作，智能体自主调用' },
  { icon: '📚', title: '参考链接', desc: '知乎/B站/豆瓣，回答附带延伸阅读' },
  { icon: '🎯', title: '向量检索', desc: 'pgvector 1536维语义检索，精准匹配' },
]

const techs = [
  'Spring Boot 3', 'LangChain4j', 'Java 21', 'PostgreSQL',
  'pgvector', 'DashScope', 'Vue 3', 'Vite', 'Docker',
  'RAG', 'Embedding', 'Reranker', 'SSE'
]

const goChat = () => router.push('/chat')
</script>

<style scoped>
.home {
  min-height: 100vh;
  padding: 0 24px;
  display: flex;
  justify-content: center;
}
.home-content {
  max-width: 900px;
  width: 100%;
  z-index: 2;
}

/* 导航 */
.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0;
  border-bottom: 1px solid var(--border);
  margin-bottom: 60px;
}
.nav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: -0.5px;
}
.logo-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--accent-gradient);
  box-shadow: 0 0 12px var(--accent-glow);
}
.nav-btn {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; padding: 8px 18px;
}

/* Hero */
.hero {
  text-align: center;
  padding: 40px 0 60px;
}
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 28px;
}
.badge-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: #22c55e;
  animation: pulse 2s infinite;
}
.hero-title {
  font-size: 4rem;
  font-weight: 800;
  letter-spacing: -2px;
  line-height: 1.1;
  margin-bottom: 16px;
}
.hero-subtitle {
  font-size: 1.2rem;
  color: var(--text-secondary);
  margin-bottom: 12px;
  letter-spacing: 2px;
  text-transform: uppercase;
}
.hero-desc {
  font-size: 0.9rem;
  color: var(--text-dim);
  margin-bottom: 36px;
}
.hero-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
}
.hero-cta {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 32px;
  font-size: 15px; font-weight: 600;
}
.hero-cta svg { transition: transform 0.3s var(--spring); }
.hero-cta:hover svg { transform: translateX(4px); }
.github-btn {
  display: flex; align-items: center; gap: 8px;
  padding: 14px 24px; font-size: 14px;
}

/* 功能 */
.features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 60px;
}
.feature-card {
  padding: 28px 24px;
  background: var(--bg-glass);
  backdrop-filter: var(--blur);
  -webkit-backdrop-filter: var(--blur);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  animation: fadeInUp 0.6s var(--spring) both;
  transition: all 0.3s var(--spring);
}
.feature-card:hover {
  border-color: var(--border-light);
  transform: translateY(-3px);
  background: var(--bg-glass-hover);
}
.feature-icon { font-size: 1.5rem; margin-bottom: 14px; }
.feature-card h3 {
  font-size: 0.95rem;
  font-weight: 600;
  margin-bottom: 8px;
}
.feature-card p {
  font-size: 0.8rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

/* 技术栈 */
.tech-section { margin-bottom: 60px; text-align: center; }
.section-title {
  font-size: 1.2rem;
  font-weight: 600;
  margin-bottom: 20px;
  color: var(--text-secondary);
}
.tech-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}
.tech-tag {
  padding: 6px 14px;
  border-radius: 8px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  font-size: 0.75rem;
  color: var(--text-secondary);
  transition: all 0.3s var(--spring);
}
.tech-tag:hover {
  border-color: var(--accent-1);
  color: var(--accent-1);
}

/* 底部 */
.home-footer {
  text-align: center;
  padding: 40px 0;
  border-top: 1px solid var(--border);
  color: var(--text-dim);
  font-size: 0.75rem;
}

@media (max-width: 768px) {
  .hero-title { font-size: 2.5rem; }
  .features { grid-template-columns: 1fr 1fr; gap: 12px; }
  .hero-actions { flex-direction: column; align-items: center; }
}
@media (max-width: 480px) {
  .hero-title { font-size: 2rem; }
  .features { grid-template-columns: 1fr; }
}
</style>
