<template>
  <div class="poi-card">
    <div class="poi-card-header">
      <span class="poi-name">{{ poi.name }}</span>
      <span v-if="poi.distance" class="poi-dist">{{ poi.distance }}m</span>
    </div>
    <div v-if="poi.address" class="poi-addr">📍 {{ poi.address }}</div>
    <div v-if="poi.rating" class="poi-rating">评分 {{ poi.rating }}</div>
    <div v-if="poi.images && poi.images.length" class="poi-images">
      <img v-for="(img, k) in poi.images.slice(0, 3)" :key="k" :src="img" class="poi-img" loading="lazy" />
    </div>
  </div>
</template>

<script setup>
defineProps({ poi: { type: Object, required: true } })
</script>

<style scoped>
.poi-card {
  padding: 12px 14px; margin: 6px 0; border-radius: 12px;
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  transition: all 0.2s;
}
.poi-card:hover { background: rgba(255,255,255,0.1); }
.poi-card-header { display: flex; justify-content: space-between; align-items: center; }
.poi-name { font-size: 0.85rem; font-weight: 600; }
.poi-dist { font-size: 0.72rem; color: var(--color-text-dim); }
.poi-addr { font-size: 0.75rem; color: var(--color-text-secondary); margin-top: 4px; }
.poi-rating { font-size: 0.75rem; color: #f59e0b; margin-top: 4px; }
.poi-images { display: flex; gap: 6px; margin-top: 8px; }
.poi-img { width: 100px; height: 75px; object-fit: cover; border-radius: 8px; border: 1px solid rgba(255,255,255,0.08); }
</style>

