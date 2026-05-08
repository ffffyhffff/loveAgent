<template>
  <div class="poi-card">
    <div class="poi-card-header">
      <span class="poi-name">{{ poi.name }}</span>
      <span v-if="poi.distance" class="poi-dist">{{ poi.distance }}m</span>
    </div>
    <div v-if="poi.address" class="poi-addr">📍 {{ poi.address }}</div>
    <div v-if="poi.rating" class="poi-rating">⭐ {{ poi.rating }}分</div>
    <div v-if="poi.comment" class="poi-comment">{{ poi.comment }}</div>
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
.poi-comment {
  font-size: 0.72rem; color: var(--color-text-secondary); margin-top: 6px;
  font-style: italic; padding: 6px 8px; background: rgba(255,255,255,0.04); border-radius: 8px;
}
</style>
