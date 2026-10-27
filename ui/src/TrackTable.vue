<script setup>
import AlbumCover from './AlbumCover.vue'
import Icon from './Icon.vue'

defineProps({ tracks: { type: Array, required: true }, showDate: { type: Boolean, default: false }, showPlays: { type: Boolean, default: false }, showReason: { type: Boolean, default: false } })
const emit = defineEmits(['select'])
</script>

<template>
  <div class="track-table">
    <div class="track-table-head">
      <span>#</span><span>歌曲</span><span class="table-album">专辑</span><span class="table-extra">{{ showReason ? '推荐理由' : showDate ? '播放时间' : showPlays ? '播放量' : '风格' }}</span><Icon name="clock" :size="17" />
    </div>
    <button v-for="(track, index) in tracks" :key="track.id" class="track-row" type="button" @click="emit('select', track)">
      <span class="track-index">{{ String(index + 1).padStart(2, '0') }}</span>
      <span class="track-main"><AlbumCover :track="track" size="tiny" /><span class="track-identity"><strong>{{ track.title }} <small v-if="track.vip" class="vip-mini">VIP</small></strong><small>{{ track.artist }}</small></span></span>
      <span class="table-album muted">{{ track.album }}</span>
      <span class="table-extra muted">{{ showReason ? track.reason : showDate ? track.date : showPlays ? track.plays : track.genreName || track.category }}</span>
      <span class="track-duration muted">{{ track.duration }}</span>
    </button>
  </div>
</template>
