import { nextTick, onUnmounted, ref } from 'vue'
import { request } from './api'

export function useAudioPlayer() {
  const current = ref(null)
  const audioElement = ref(null)
  const audioUrl = ref('')
  const playing = ref(false)
  const loading = ref(false)
  const playbackError = ref('')
  const progress = ref(0)
  const playbackDuration = ref(0)
  let controller = null
  let selection = 0
  let reported = false

  function stop() {
    selection++
    controller?.abort()
    controller = null
    audioElement.value?.pause()
    if (audioUrl.value) URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = ''
    current.value = null
    playing.value = false
    loading.value = false
    playbackError.value = ''
    progress.value = 0
    playbackDuration.value = 0
    reported = false
  }

  async function load(track) {
    const id = ++selection
    controller?.abort()
    controller = new AbortController()
    audioElement.value?.pause()
    audioElement.value?.removeAttribute('src')
    audioElement.value?.load()
    if (audioUrl.value) URL.revokeObjectURL(audioUrl.value)
    audioUrl.value = ''
    current.value = track
    playing.value = false
    loading.value = true
    playbackError.value = ''
    progress.value = 0
    playbackDuration.value = 0
    reported = false
    try {
      const response = await request(`/songs/${track.id}/audio`, { raw: true, signal: controller.signal })
      const blob = await response.blob()
      if (id !== selection) return false
      if (!blob.size || !/audio\//i.test(response.headers.get('Content-Type') || '')) throw new Error('音频文件响应无效，请检查后端服务')
      audioUrl.value = URL.createObjectURL(blob)
      await nextTick()
      if (id !== selection) return false
      audioElement.value?.load()
      try { await audioElement.value?.play() }
      catch (error) { if (id === selection && error.name !== 'NotAllowedError' && error.name !== 'AbortError') playbackError.value = '音频无法播放，请重试' }
      return true
    } catch (error) {
      if (id === selection && error.name !== 'AbortError') playbackError.value = error.message
      return false
    } finally {
      if (id === selection) loading.value = false
    }
  }

  function toggle() {
    if (!audioElement.value || !audioUrl.value) return
    if (audioElement.value.paused) audioElement.value.play().then(() => { playbackError.value = '' }).catch(() => { playbackError.value = '无法播放音频，请重试' })
    else audioElement.value.pause()
  }

  function seek(event) {
    const duration = audioElement.value?.duration
    if (Number.isFinite(duration)) audioElement.value.currentTime = Math.min(Number(event.target.value), duration)
  }

  function onTimeUpdate() {
    if (!audioElement.value || !current.value) return
    progress.value = audioElement.value.currentTime
    if (!reported && progress.value >= 10) {
      reported = true
      const songId = current.value.id
      request('/me/play-events', { method: 'POST', body: { songId, secondsPlayed: Math.floor(progress.value), completed: false } }).catch(() => {})
    }
  }

  function onEnded() { playing.value = false }
  function onError() {
    if (audioUrl.value && audioElement.value?.currentSrc === audioUrl.value) playbackError.value = '音频无法播放，请重试'
  }
  onUnmounted(stop)
  return { current, audioElement, audioUrl, playing, loading, playbackError, progress, playbackDuration, load, stop, toggle, seek, onTimeUpdate, onEnded, onError }
}
