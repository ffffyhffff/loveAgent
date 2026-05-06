<template>
  <div class="app-root">
    <div class="bg-glow bg-glow-1"></div>
    <div class="bg-glow bg-glow-2"></div>
    <div class="bg-glow bg-glow-3"></div>
    <router-view />
  </div>
</template>

<script setup>
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
html, body { width: 100%; height: 100%; overflow-x: hidden; }
.app-root { position: relative; min-height: 100vh; z-index: 1; }
a { text-decoration: none; color: inherit; }
</style>
