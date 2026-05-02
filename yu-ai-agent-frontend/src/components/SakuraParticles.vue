<template>
  <div class="sakura-container" ref="container"></div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const container = ref(null)
let timer = null

const petalTypes = ['', 'light', 'dark']

const createPetal = () => {
  if (!container.value) return

  const petal = document.createElement('div')
  petal.className = 'sakura-petal'

  const inner = document.createElement('div')
  const type = petalTypes[Math.floor(Math.random() * petalTypes.length)]
  inner.className = `petal ${type}`
  petal.appendChild(inner)

  // 随机起始位置
  const startX = Math.random() * window.innerWidth
  petal.style.left = `${startX}px`
  petal.style.top = `-60px`

  // 随机大小缩放
  const scale = 0.5 + Math.random() * 1
  inner.style.transform = `rotate(45deg) scale(${scale})`

  // 随机摆动
  const sway = (Math.random() - 0.5) * 300
  const swayEnd = sway + (Math.random() - 0.5) * 200
  petal.style.setProperty('--sway', `${sway}px`)
  petal.style.setProperty('--sway-end', `${swayEnd}px`)

  // 随机动画时长
  const duration = 6 + Math.random() * 8
  petal.style.animation = `sakuraFall ${duration}s linear forwards`

  container.value.appendChild(petal)

  // 动画结束后移除
  setTimeout(() => {
    petal.remove()
  }, duration * 1000)
}

onMounted(() => {
  // 立即创建几个
  for (let i = 0; i < 8; i++) {
    setTimeout(createPetal, i * 300)
  }
  // 持续生成花瓣
  timer = setInterval(createPetal, 600)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>
