<template>
  <div class="app-root">
    <!-- 背景光斑 -->
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>
    <div class="bg-orb bg-orb-3"></div>
    <!-- 樱花飘落 -->
    <SakuraParticles />
    <router-view />
  </div>
</template>

<script setup>
import SakuraParticles from './components/SakuraParticles.vue'
</script>

<style>
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body {
  width: 100%;
  height: 100%;
  overflow-x: hidden;
}

.app-root {
  position: relative;
  min-height: 100vh;
  z-index: 1;
}

a {
  text-decoration: none;
  color: inherit;
}
</style>
