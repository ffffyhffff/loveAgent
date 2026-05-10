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
  inner.className = `petal ${petalTypes[Math.floor(Math.random() * petalTypes.length)]}`
  petal.appendChild(inner)
  petal.style.left = `${Math.random() * window.innerWidth}px`
  petal.style.top = `-60px`
  const scale = 0.5 + Math.random() * 1
  inner.style.transform = `rotate(45deg) scale(${scale})`
  petal.style.setProperty('--sway', `${(Math.random() - 0.5) * 300}px`)
  petal.style.setProperty('--sway-end', `${(Math.random() - 0.5) * 200}px`)
  const duration = 6 + Math.random() * 8
  petal.style.animation = `sakuraFall ${duration}s linear forwards`
  container.value.appendChild(petal)
  setTimeout(() => petal.remove(), duration * 1000)
}
onMounted(() => {
  for (let i = 0; i < 8; i++) setTimeout(createPetal, i * 300)
  timer = setInterval(createPetal, 600)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
