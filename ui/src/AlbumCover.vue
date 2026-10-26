<script setup>
defineProps({ track: { type: Object, required: true }, size: { type: String, default: 'medium' }, showTitle: { type: Boolean, default: false } })
</script>

<template>
  <div class="album-cover" :class="[track.tone, `album-cover--${size}`]" role="img" :aria-label="`${track.title}的封面`">
    <img v-if="track.coverUrl" :key="track.coverUrl" class="cover-image" :src="track.coverUrl" :alt="`${track.title}的封面`" loading="lazy" @error="$event.target.style.display = 'none'" />
    <span class="cover-orbit orbit-one"></span>
    <span class="cover-orbit orbit-two"></span>
    <span class="cover-sun"></span>
    <span class="cover-grain"></span>
    <span v-if="showTitle" class="cover-wordmark">{{ track.title }}</span>
    <span v-if="showTitle" class="cover-caption">SHENGYU / ORIGINAL MIX</span>
  </div>
</template>
