<script setup>
defineProps({ name: { type: String, required: true }, size: { type: Number, default: 20 } })

const paths = {
  home: '<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"/><path d="M9 21v-8h6v8"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/>',
  disc: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="2"/><path d="M12 3a9 9 0 0 1 9 9"/>',
  spark: '<path d="m12 2 1.9 6.1L20 10l-6.1 1.9L12 18l-1.9-6.1L4 10l6.1-1.9zM19 17l.6 1.4L21 19l-1.4.6L19 21l-.6-1.4L17 19l1.4-.6z"/>',
  heart: '<path d="M20.8 4.6a5.4 5.4 0 0 0-7.6 0L12 5.8l-1.2-1.2a5.4 5.4 0 0 0-7.6 7.6c0 0 8.8 8.6 8.8 8.6s8.8-8.6 8.8-8.6a5.4 5.4 0 0 0 0-7.6z"/>',
  list: '<path d="M4 6h16M4 12h11M4 18h11"/><path d="M19 14v7m-3.5-3.5H22"/>',
  history: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5M12 7v5l3.5 2"/>',
  crown: '<path d="m2 7 5 4 5-7 5 7 5-4-2 12H4z"/><path d="M4 22h16"/>',
  chart: '<path d="M4 20V12m5 8V7m5 13v-5m5 5V4"/>',
  users: '<circle cx="9" cy="8" r="3"/><path d="M3 20v-2a6 6 0 0 1 12 0v2M17 5a3 3 0 0 1 0 6m1 3a5 5 0 0 1 3 5v1"/>',
  music: '<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>',
  comment: '<path d="M21 11.5a8.5 8.5 0 0 1-8.5 8.5 9 9 0 0 1-4-.9L3 21l1.9-5.5a9 9 0 0 1-.9-4A8.5 8.5 0 0 1 12.5 3 8.5 8.5 0 0 1 21 11.5z"/>',
  play: '<path d="m8 5 11 7-11 7z"/>',
  pause: '<path d="M7 5v14M17 5v14"/>',
  next: '<path d="m5 5 10 7-10 7zM19 5v14"/>',
  prev: '<path d="M19 5 9 12l10 7zM5 5v14"/>',
  shuffle: '<path d="M3 6h3c5 0 7 12 12 12h3m-4-3 3 3-3 3M3 18h3c2 0 3-2 4-4m4-4c1-2 2-4 4-4h3m-4-3 3 3-3 3"/>',
  repeat: '<path d="M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4m14-1v2a3 3 0 0 1-3 3H3"/>',
  volume: '<path d="M4 9v6h4l5 4V5L8 9zM17 9a5 5 0 0 1 0 6m2-9a9 9 0 0 1 0 12"/>',
  arrow: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  filter: '<path d="M4 7h16M7 12h10m-7 5h4"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  headphones: '<path d="M3 13v-2a9 9 0 0 1 18 0v2M3 13h3v7H4a1 1 0 0 1-1-1zm18 0h-3v7h2a1 1 0 0 0 1-1z"/>',
  send: '<path d="m22 2-7 20-4-9-9-4zM22 2 11 13"/>',
  check: '<path d="m4 12 5 5L20 6"/>',
  upload: '<path d="M12 16V3m-5 5 5-5 5 5M4 17v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  close: '<path d="M5 5 19 19M19 5 5 19"/>',
  logout: '<path d="M10 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h5m4-4 5-5-5-5m5 5H9"/>'
}
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="paths[name] || paths.music" />
</template>
