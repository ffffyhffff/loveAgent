<template>
  <div class="route-map-wrapper glass-card">
    <h4 class="map-title">路线规划</h4>
    <div class="map-container" ref="mapRef"></div>
    <div class="route-info" v-if="totalDistance || totalDuration">
      <span v-if="totalDistance">🚶 {{ totalDistance }}</span>
      <span v-if="totalDuration">⏱️ {{ totalDuration }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

const props = defineProps({
  pois: { type: Array, default: () => [] },
  routeInfo: { type: Object, default: null },
})

const mapRef = ref(null)
const totalDistance = ref(props.routeInfo?.distance || '')
const totalDuration = ref(props.routeInfo?.duration || '')
let map = null

const AMAP_KEY = '94601f2760428adee8c2ae25f257280e'

function loadAMap() {
  return new Promise((resolve, reject) => {
    if (window.AMap) { resolve(window.AMap); return }
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_KEY}&plugin=AMap.Walking,AMap.Driving,AMap.Marker`
    script.onload = () => setTimeout(() => resolve(window.AMap), 100)
    script.onerror = reject
    document.head.appendChild(script)
  })
}

onMounted(async () => {
  if (!mapRef.value || props.pois.length === 0) return

  await nextTick()

  try {
    const AMap = await loadAMap()

    // 确保容器有尺寸
    const container = mapRef.value
    if (container.offsetWidth === 0 || container.offsetHeight === 0) {
      console.warn('地图容器尺寸为 0，延迟初始化')
      setTimeout(() => initMap(AMap), 500)
      return
    }

    initMap(AMap)
  } catch (e) {
    console.error('地图加载失败', e)
  }
})

function initMap(AMap) {
  const container = mapRef.value
  if (!container) return

  map = new AMap.Map(container, {
    zoom: 14,
    mapStyle: 'amap://styles/whitesmoke',
    viewMode: '2D',
    resizeEnable: true,
  })

  const markers = []
  const colors = ['#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#3b82f6']

  props.pois.forEach((poi, i) => {
    if (!poi.longitude || !poi.latitude) return
    const color = colors[i % colors.length]

    const marker = new AMap.Marker({
      position: [poi.longitude, poi.latitude],
      title: poi.name,
      content: `<div style="background:${color};color:#fff;padding:4px 12px;border-radius:20px;font-size:13px;font-weight:600;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,0.3)">${i + 1}. ${poi.name}</div>`,
      offset: new AMap.Pixel(-20, -12),
      anchor: 'center',
    })
    markers.push(marker)
  })

  if (markers.length > 0) {
    map.add(markers)
  }

  // 查询步行距离，但路线统一用自定义折线绘制，避免高德默认标记覆盖 1/2/3 编号
  if (props.pois.length >= 2) {
    let totalDist = 0
    let totalTime = 0
    let completed = 0
    const segments = props.pois.length - 1

    for (let i = 0; i < segments; i++) {
      const from = props.pois[i]
      const to = props.pois[i + 1]
      if (!from.longitude || !to.longitude) continue

      const walking = new AMap.Walking({
        hideMarkers: true,
      })

      walking.search(
        new AMap.LngLat(from.longitude, from.latitude),
        new AMap.LngLat(to.longitude, to.latitude),
        (status, result) => {
          completed++
          if (status === 'complete' && result.routes && result.routes.length > 0) {
            totalDist += result.routes[0].distance || 0
            totalTime += result.routes[0].time || 0
          } else {
            console.warn(`步行路线规划失败 (${from.name} -> ${to.name}):`, status, result)
          }

          // 所有段完成后
          if (completed === segments) {
            // Walking API 画线成功时有距离
            if (totalDist > 0 && !totalDistance.value) {
              totalDistance.value = (totalDist / 1000).toFixed(1) + 'km'
            }
            if (totalTime > 0 && !totalDuration.value) {
              totalDuration.value = Math.round(totalTime / 60) + '分钟'
            }
            drawFallbackLine(AMap)
          }
        }
      )
    }
  }

  // Fallback：手动画线连接 POI
  function drawFallbackLine(AMap) {
    const linePath = props.pois
      .filter(p => p.longitude && p.latitude)
      .map(p => new AMap.LngLat(p.longitude, p.latitude))

    if (linePath.length < 2) return

    const polyline = new AMap.Polyline({
      path: linePath,
      strokeColor: '#ec4899',
      strokeWeight: 4,
      strokeStyle: 'dashed',
      strokeOpacity: 0.8,
      lineJoin: 'round',
    })
    map.add(polyline)

    // 计算 fallback 距离（直线距离）
    let dist = 0
    for (let i = 0; i < linePath.length - 1; i++) {
      dist += linePath[i].distance(linePath[i + 1])
    }
    if (dist > 0 && !totalDistance.value) {
      totalDistance.value = '约 ' + (dist / 1000).toFixed(1) + 'km（直线距离）'
    }
  }

  // 自动调整视野
  setTimeout(() => {
    if (markers.length > 0) {
      map.setFitView(markers, false, [60, 60, 60, 60])
    }
  }, 1500)
}

onUnmounted(() => {
  if (map) { map.destroy(); map = null }
})
</script>

<style scoped>
.route-map-wrapper { padding: 12px; margin: 8px 0; }
.map-title { font-size: 0.9rem; font-weight: 600; margin-bottom: 10px; }
.map-container {
  width: 100%;
  height: 350px;
  min-height: 280px;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(0,0,0,0.05);
}
.route-info {
  display: flex; gap: 16px; margin-top: 10px;
  font-size: 0.85rem; color: var(--color-text-secondary);
}
</style>

