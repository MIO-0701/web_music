<script setup>
import { computed } from 'vue'

const props = defineProps({ dashboard: { type: Object, required: true } })
const days = computed(() => {
  const counts = new Map((props.dashboard.dailyPlays || []).map(row => [String(row.day).slice(0, 10), Number(row.plays)]))
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date()
    date.setDate(date.getDate() - (6 - index))
    const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    return { label: key.slice(5), count: counts.get(key) || 0 }
  })
})
const maxDaily = computed(() => Math.max(1, ...days.value.map(item => item.count)))
const genres = computed(() => (props.dashboard.genres || []).map(row => ({ name: row.genre, count: Number(row.count) })))
const maxGenre = computed(() => Math.max(1, ...genres.value.map(item => item.count)))
</script>

<template>
  <div class="dashboard-grid live-chart-grid">
    <section class="dashboard-panel"><div class="panel-heading"><h3>近七天播放量</h3></div>
      <div class="daily-chart" role="img" aria-label="近七天播放量柱状图">
        <div v-for="day in days" :key="day.label" class="daily-column"><strong>{{ day.count }}</strong><div class="daily-bar-wrap"><span :style="{ height: `${Math.max(day.count ? 8 : 2, day.count / maxDaily * 100)}%` }"></span></div><small>{{ day.label }}</small></div>
      </div>
    </section>
    <section class="dashboard-panel"><div class="panel-heading"><h3>曲风分布</h3></div>
      <div v-if="genres.length" class="genre-chart"><div v-for="genre in genres" :key="genre.name" class="genre-row"><span>{{ genre.name }}</span><div><i :style="{ width: `${genre.count / maxGenre * 100}%` }"></i></div><strong>{{ genre.count }}</strong></div></div>
      <p v-else class="empty-state">暂无已上架歌曲。</p>
    </section>
  </div>
</template>
