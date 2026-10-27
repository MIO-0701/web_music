<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AlbumCover from './AlbumCover.vue'
import DashboardCharts from './DashboardCharts.vue'
import Icon from './Icon.vue'
import TrackTable from './TrackTable.vue'
import { genreNames, getToken, queryString, request, setToken, songView } from './api'
import { useAudioPlayer } from './useAudioPlayer'

const userNav = [
  ['home', '发现音乐', 'home'], ['search', '曲库检索', 'search'], ['player', '播放页面', 'disc'],
  ['recommend', '每日推荐', 'spark'], ['ai', 'AI 查歌', 'spark'], ['favorites', '我的收藏', 'heart'],
  ['playlists', '我的歌单', 'list'], ['history', '播放历史', 'history'], ['membership', '会员中心', 'crown']
]
const adminNav = [['dashboard', '数据概览', 'chart'], ['members', '用户与会员', 'users'], ['songs', '歌曲管理', 'music'], ['comments', '评论管理', 'comment']]
const labels = Object.fromEntries([...userNav, ...adminNav].map(([id, label]) => [id, label]))
labels['playlist-detail'] = '我的歌单'
const page = ref('home')
const mobileOpen = ref(false)
const user = ref(null)
const loginOpen = ref(false)
const authMode = ref('login')
const loginName = ref('')
const loginPassword = ref('')
const registerName = ref('')
const pendingPage = ref('home')
const busy = ref(false)
const toast = ref('')
const error = ref('')
const hot = ref([])
const languageSections = ref([])
const languageOptions = [['cn', '中文歌曲', 'CHINESE'], ['en', '英文歌曲', 'ENGLISH'], ['jp', '日文歌曲', 'JAPANESE'], ['fr', '法文歌曲', 'FRENCH'], ['ru', '俄文歌曲', 'RUSSIAN']]
const songs = ref([])
const total = ref(0)
const searchLoading = ref(false)
const listPage = ref(1)
const keyword = ref('')
const language = ref('')
const genre = ref('')
const genreOptions = ref([])
const { current, audioElement, audioUrl, playing, loading: audioLoading, playbackError, progress, playbackDuration,
  load: loadAudio, stop: stopAudio, toggle: togglePlayback, seek: seekAudio, onTimeUpdate, onEnded: markEnded, onError: audioError } = useAudioPlayer()
const playQueue = ref([])
const queueOpen = ref(false)
const playMode = ref('LIST')
const queueIndex = computed(() => playQueue.value.findIndex(song => song.id === current.value?.id))
const playModeLabel = computed(() => ({ LIST: '顺序播放', LOOP: '列表循环', ONE: '单曲循环' })[playMode.value])
const favorites = ref([])
const favoriteIds = ref(new Set())
const favoritePending = ref(new Set())
const favoritesPage = ref(1)
const favoritesTotal = ref(0)
const favoritesLoading = ref(false)
const favoriteKeyword = ref('')
const playlists = ref([])
const playlistName = ref('')
const activePlaylist = ref(null)
const playlistRename = ref('')
const playlistPickerOpen = ref(false)
const playlistPickerTrack = ref(null)
const pendingPlaylistId = ref(null)
const vipSongs = ref([])
const vipPage = ref(1)
const vipTotal = ref(0)
const vipLoading = ref(false)
const history = ref([])
const historyPage = ref(1)
const historyTotal = ref(0)
const historyLoading = ref(false)
const recommendations = ref([])
const chatText = ref('')
const conversationId = ref(null)
const conversations = ref([])
const messages = ref([])
const songComments = ref([])
const commentText = ref('')
const dashboard = ref(null)
const adminUsers = ref([])
const adminSongs = ref([])
const adminComments = ref([])
const adminTotal = ref(0)
const adminUserKeyword = ref('')
const adminSongKeyword = ref('')
const adminCommentStatus = ref('')
const membershipDraft = ref({})
const songEdit = ref(null)
const uploadOpen = ref(false)
const uploadForm = ref({ title: '', artist: '', album: '', language: 'cn', genre: '未分类', tags: '', vip: false, audio: null, cover: null })
let toastTimer, searchTimer, adminSearchTimer, chatRequestId = 0, sessionEpoch = 0, searchRequestId = 0
let favoritesRequestId = 0, historyRequestId = 0, favoriteSearchTimer

const isAdminPage = computed(() => adminNav.some(([id]) => id === page.value))
const isMember = computed(() => !!user.value?.membershipExpiresAt && new Date(user.value.membershipExpiresAt).getTime() > Date.now())
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const gatedPages = new Set(['player', 'recommend', 'ai', 'favorites', 'playlists', 'playlist-detail', 'history', 'membership'])

function notify(message) { toast.value = message; clearTimeout(toastTimer); toastTimer = setTimeout(() => { toast.value = '' }, 3600) }
async function run(work) { try { busy.value = true; error.value = ''; return await work() } catch (ex) { if (ex.status !== 401) { error.value = ex.message; notify(ex.message) } } finally { busy.value = false } }
function requireLogin(target = page.value) { if (user.value) return true; pendingPage.value = target; loginOpen.value = true; return false }
function navigate(id) {
  if (adminNav.some(([key]) => key === id) && !isAdmin.value) { if (!user.value) { pendingPage.value = id; loginOpen.value = true } else notify('需要管理员权限'); return }
  if (gatedPages.has(id) && !requireLogin(id)) return
  page.value = id; listPage.value = 1; mobileOpen.value = false; window.location.hash = id
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
async function login() {
  await run(async () => {
    const response = await request(authMode.value === 'login' ? '/auth/login' : '/auth/register', {
      method: 'POST', body: authMode.value === 'login'
        ? { username: loginName.value, password: loginPassword.value }
        : { username: loginName.value, password: loginPassword.value, displayName: registerName.value }
    })
    setToken(response.token); user.value = response.user; loginOpen.value = false; loginPassword.value = ''
    notify(`欢迎，${user.value.displayName}`)
    navigate(pendingPage.value)
    if (pendingPlaylistId.value) { const id = pendingPlaylistId.value; pendingPlaylistId.value = null; await openPlaylist(id) }
    await Promise.all([loadHot(), loadFavorites(), loadFavoriteIds()])
  })
}
async function logout() {
  try { await request('/auth/logout', { method: 'POST' }) } catch { /* local logout still applies */ }
  clearSession(); notify('已退出登录')
}
function clearSession() {
  sessionEpoch++; favoritesRequestId++; historyRequestId++
  setToken(''); user.value = null; stopAudio(); chatRequestId++
  playQueue.value = []; queueOpen.value = false
  favorites.value = []; favoriteIds.value = new Set(); favoritesPage.value = 1; favoritesTotal.value = 0
  favoritePending.value = new Set()
  favoritesLoading.value = false
  playlists.value = []; activePlaylist.value = null; playlistPickerOpen.value = false; playlistPickerTrack.value = null; songComments.value = []
  history.value = []; historyPage.value = 1; historyTotal.value = 0; recommendations.value = []
  vipSongs.value = []; vipPage.value = 1; vipTotal.value = 0; favoriteKeyword.value = ''
  pendingPlaylistId.value = null
  historyLoading.value = false
  conversations.value = []; messages.value = []; conversationId.value = null
  dashboard.value = null; adminUsers.value = []; adminSongs.value = []; adminComments.value = []; adminTotal.value = 0
  membershipDraft.value = {}; songEdit.value = null; uploadOpen.value = false
  error.value = ''
  loginOpen.value = false; page.value = 'home'; window.location.hash = 'home'
}
function authExpired() { clearSession(); notify('登录已过期，请重新登录') }
async function loadHot() {
  await run(async () => {
    hot.value = (await request('/songs/hot')).map(songView)
    const sections = await Promise.all(languageOptions.map(async ([code, title, subtitle]) => {
      try { const data = await request(`/songs?language=${code}&page=1&size=8`); return { code, title, subtitle, tracks: data.items.map(songView) } }
      catch { return { code, title, subtitle, tracks: [] } }
    }))
    languageSections.value = sections.filter(section => section.tracks.length)
    genreOptions.value = await request('/songs/genres')
  })
}
async function loadSearch() {
  const requestId = ++searchRequestId
  searchLoading.value = true; songs.value = []
  try {
    const data = await request(`/songs?${queryString({ keyword: keyword.value, language: language.value, genre: genre.value, page: listPage.value, size: 20 })}`)
    if (requestId === searchRequestId) { songs.value = data.items.map(songView); total.value = data.total }
  } catch (ex) {
    if (requestId === searchRequestId) { error.value = ex.message; notify(ex.message) }
  } finally { if (requestId === searchRequestId) searchLoading.value = false }
}
async function playSong(track, source = []) {
  if (!requireLogin('player')) return
  const selected = songView(track)
  if (source.length) playQueue.value = source.map(songView)
  else if (!playQueue.value.some(song => song.id === selected.id)) playQueue.value = [selected]
  navigate('player')
  songComments.value = []
  const loaded = await loadAudio(selected)
  if (loaded && current.value?.id === selected.id) await loadSongComments(selected.id)
}
function cyclePlayMode() { playMode.value = ({ LIST: 'LOOP', LOOP: 'ONE', ONE: 'LIST' })[playMode.value]; notify(`已切换为${playModeLabel.value}`) }
function togglePlaybackWithHint() {
  if (audioLoading.value) { notify('音频正在加载'); return }
  if (!audioUrl.value) { notify('请先选择一首歌曲'); return }
  const wasPlaying = playing.value
  togglePlayback()
  notify(wasPlaying ? '已暂停播放' : '正在播放')
}
function showQueue() { queueOpen.value = !queueOpen.value; notify(queueOpen.value ? `播放列表已打开，共 ${playQueue.value.length} 首` : '播放列表已关闭') }
function showPlayer() { if (!current.value) { notify('请先选择一首歌曲'); return }; navigate('player'); notify('已打开歌曲页面') }
async function skipSong(direction, automatic = false) {
  if (!playQueue.value.length || queueIndex.value < 0) { if (!automatic) notify('播放列表中还没有歌曲'); return }
  const next = queueIndex.value + direction
  let selected = null
  if (next >= 0 && next < playQueue.value.length) selected = playQueue.value[next]
  else if (!automatic || playMode.value === 'LOOP') selected = playQueue.value[(next + playQueue.value.length) % playQueue.value.length]
  if (selected) { await playSong(selected); if (!automatic) notify(`已切换到《${selected.title}》`) }
  else if (!automatic) notify('已经是播放列表的最后一首')
}
async function onEnded() {
  markEnded()
  if (playMode.value === 'ONE' && audioElement.value) {
    audioElement.value.currentTime = 0
    audioElement.value.play().catch(() => { playbackError.value = '音频无法播放，请重试' })
  } else await skipSong(1, true)
}
function formatTime(value) {
  const seconds = Number.isFinite(value) ? Math.floor(value) : 0
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}
async function loadSongComments(songId = current.value?.id) {
  if (!songId) return
  try {
    const data = await request(`/songs/${songId}/comments`)
    if (current.value?.id === songId) songComments.value = data.items
  } catch (ex) { if (current.value?.id === songId) notify(ex.message) }
}
async function submitComment() {
  if (!current.value || !commentText.value.trim()) return
  await run(async () => { await request(`/songs/${current.value.id}/comments`, { method: 'POST', body: { content: commentText.value.trim() } }); commentText.value = ''; notify('评论已提交，等待审核') })
}
async function loadFavorites() {
  if (!user.value) return
  const epoch = sessionEpoch, requestId = ++favoritesRequestId
  favoritesLoading.value = true; favorites.value = []
  try {
    const data = await request(`/me/favorites?${queryString({ keyword: favoriteKeyword.value, page: favoritesPage.value, size: 20 })}`)
    if (epoch !== sessionEpoch || requestId !== favoritesRequestId) return
    favorites.value = data.items.map(songView); favoritesTotal.value = data.total
    if (favoritesPage.value > 1 && !favorites.value.length) { favoritesPage.value--; await loadFavorites() }
  } finally { if (requestId === favoritesRequestId) favoritesLoading.value = false }
}
async function loadFavoriteIds() {
  if (!user.value) return
  const epoch = sessionEpoch
  const ids = new Set()
  let index = 1, totalCount = 0
  do {
    const data = await request(`/me/favorites?page=${index}&size=100`)
    if (epoch !== sessionEpoch) return
    data.items.forEach(item => ids.add(Number(item.id)))
    totalCount = data.total
    index++
  } while (ids.size < totalCount && index <= Math.ceil(totalCount / 100))
  if (epoch === sessionEpoch) favoriteIds.value = ids
}
async function toggleFavorite(track) {
  if (!requireLogin()) return
  if (favoritePending.value.has(track.id)) return
  const alreadySaved = favoriteIds.value.has(track.id)
  const updated = new Set(favoriteIds.value)
  if (alreadySaved) updated.delete(track.id); else updated.add(track.id)
  favoriteIds.value = updated
  favoritePending.value = new Set([...favoritePending.value, track.id])
  try {
    await request(`/me/favorites/${track.id}`, { method: alreadySaved ? 'DELETE' : 'POST' })
    notify(alreadySaved ? '已取消收藏' : '已收藏')
    await loadFavorites().catch(ex => { if (ex.status !== 401) notify('收藏列表刷新失败，请稍后重试') })
  } catch (ex) {
    const rollback = new Set(favoriteIds.value)
    if (alreadySaved) rollback.add(track.id); else rollback.delete(track.id)
    favoriteIds.value = rollback
    if (ex.status !== 401) notify(ex.message)
  } finally {
    const pending = new Set(favoritePending.value)
    pending.delete(track.id)
    favoritePending.value = pending
  }
}
async function loadPlaylists() {
  const epoch = sessionEpoch
  const data = await request('/me/playlists')
  if (epoch === sessionEpoch) playlists.value = data
}
async function createPlaylist() {
  if (!playlistName.value.trim()) return
  await run(async () => { await request('/me/playlists', { method: 'POST', body: { name: playlistName.value.trim() } }); playlistName.value = ''; await loadPlaylists(); notify('歌单已创建') })
}
async function openPlaylist(id) {
  await run(async () => {
    const epoch = sessionEpoch
    const data = await request(`/me/playlists/${id}`)
    if (epoch !== sessionEpoch) return
    activePlaylist.value = { ...data, songs: data.songs.map(songView) }; playlistRename.value = data.name
    page.value = 'playlist-detail'; window.location.hash = `playlist/${id}`; window.scrollTo(0, 0)
  })
}
async function renamePlaylist() {
  if (!activePlaylist.value || !playlistRename.value.trim()) return
  await run(async () => { await request(`/me/playlists/${activePlaylist.value.id}`, { method: 'PUT', body: { name: playlistRename.value.trim() } }); await loadPlaylists(); await openPlaylist(activePlaylist.value.id); notify('歌单已重命名') })
}
async function deletePlaylist() {
  if (!activePlaylist.value || !window.confirm(`确定删除“${activePlaylist.value.name}”？`)) return
  await run(async () => { await request(`/me/playlists/${activePlaylist.value.id}`, { method: 'DELETE' }); activePlaylist.value = null; navigate('playlists'); await loadPlaylists(); notify('歌单已删除') })
}
async function openPlaylistPicker(track = current.value) {
  if (!track || !requireLogin()) return
  playlistPickerTrack.value = songView(track)
  playlistPickerOpen.value = true
  try { await loadPlaylists() } catch (ex) { notify(ex.message) }
}
async function addToPlaylist(id) {
  if (!playlistPickerTrack.value) return
  await run(async () => {
    await request(`/me/playlists/${id}/songs/${playlistPickerTrack.value.id}`, { method: 'POST' })
    const name = playlists.value.find(list => list.id === id)?.name || '歌单'
    playlistPickerOpen.value = false; playlistPickerTrack.value = null
    await loadPlaylists()
    notify(`已加入「${name}」`)
  })
}
async function removeFromPlaylist(songId) {
  await run(async () => { await request(`/me/playlists/${activePlaylist.value.id}/songs/${songId}`, { method: 'DELETE' }); await openPlaylist(activePlaylist.value.id) })
}
async function loadHistory() {
  const epoch = sessionEpoch, requestId = ++historyRequestId
  historyLoading.value = true; history.value = []
  try {
    const data = await request(`/me/history?page=${historyPage.value}&size=20`)
    if (epoch !== sessionEpoch || requestId !== historyRequestId) return
    history.value = data.items.map(songView); historyTotal.value = data.total
  } finally { if (requestId === historyRequestId) historyLoading.value = false }
}
async function loadVipSongs() {
  const epoch = sessionEpoch
  vipLoading.value = true
  try {
    const data = await request(`/songs?${queryString({ vip: true, page: vipPage.value, size: 20 })}`)
    if (epoch === sessionEpoch) { vipSongs.value = data.items.map(songView); vipTotal.value = data.total }
  } finally { vipLoading.value = false }
}
async function loadRecommendations() {
  await run(async () => {
    const epoch = sessionEpoch
    const data = await request('/me/recommendations/daily')
    if (epoch === sessionEpoch) recommendations.value = data.map(songView)
  })
}
async function loadAi() {
  const epoch = sessionEpoch
  const data = await request('/me/ai/conversations')
  if (epoch === sessionEpoch) conversations.value = data
}
async function openConversation(id) {
  chatRequestId++
  await run(async () => {
    const epoch = sessionEpoch
    conversationId.value = id
    const old = await request(`/me/ai/conversations/${id}/messages`)
    const restored = await Promise.all(old.map(async item => {
      if (item.role !== 'ASSISTANT' || !item.result_song_ids) return item
      let ids = []
      try { ids = typeof item.result_song_ids === 'string' ? JSON.parse(item.result_song_ids) : item.result_song_ids } catch { return item }
      const songs = await Promise.all(ids.slice(0, 10).map(songId => request(`/songs/${songId}`).then(songView).catch(() => null)))
      return { ...item, songs: songs.filter(Boolean) }
    }))
    if (epoch !== sessionEpoch || conversationId.value !== id) return
    messages.value = restored
    navigate('ai')
  })
}
function newConversation() { chatRequestId++; conversationId.value = null; messages.value = []; chatText.value = '' }
async function sendChat() {
  const text = chatText.value.trim(); if (!text) return
  const id = ++chatRequestId
  const previousConversation = conversationId.value
  const temporaryId = `temp-${id}`
  chatText.value = ''
  messages.value.push({ role: 'USER', content: text, id: temporaryId })
  busy.value = true; error.value = ''
  try {
    const data = await request('/me/ai/chat', { method: 'POST', body: { conversationId: conversationId.value, message: text } })
    if (id === chatRequestId) {
      conversationId.value = data.conversationId
      messages.value.push({ role: 'ASSISTANT', content: data.reply, songs: data.songs.map(songView), id: `answer-${id}` })
    }
    await loadAi()
  } catch (ex) {
    if (id === chatRequestId && ex.status !== 401) {
      messages.value = messages.value.filter(item => item.id !== temporaryId)
      chatText.value = text
      error.value = ex.message; notify(ex.message)
      if (previousConversation && user.value) await openConversation(previousConversation)
      else if (user.value) await loadAi().catch(() => {})
    }
  } finally { busy.value = false }
}
async function loadDashboard() {
  const epoch = sessionEpoch
  const data = await request('/admin/dashboard')
  if (epoch === sessionEpoch) dashboard.value = data
}
async function loadAdminUsers() {
  const epoch = sessionEpoch, selectedPage = listPage.value, selectedKeyword = adminUserKeyword.value
  const data = await request(`/admin/users?${queryString({ keyword: adminUserKeyword.value, page: listPage.value, size: 10 })}`)
  if (epoch !== sessionEpoch || page.value !== 'members' || selectedPage !== listPage.value || selectedKeyword !== adminUserKeyword.value) return
  adminUsers.value = data.items; adminTotal.value = data.total
  membershipDraft.value = Object.fromEntries(data.items.map(item => [item.id, item.membership_expires_at ? String(item.membership_expires_at).slice(0, 16) : '']))
}
async function saveMembership(item) {
  await run(async () => {
    const value = membershipDraft.value[item.id]
    await request(`/admin/users/${item.id}/membership`, { method: 'PUT', body: { expiresAt: value ? `${value}:00` : null } })
    await loadAdminUsers(); notify('会员有效期已更新')
  })
}
async function toggleUser(item) {
  await run(async () => { await request(`/admin/users/${item.id}/enabled?value=${!item.enabled}`, { method: 'PATCH' }); await loadAdminUsers() })
}
async function loadAdminSongs() {
  const epoch = sessionEpoch, selectedPage = listPage.value, selectedKeyword = adminSongKeyword.value
  const data = await request(`/admin/songs?${queryString({ keyword: adminSongKeyword.value, page: listPage.value, size: 10 })}`)
  if (epoch !== sessionEpoch || page.value !== 'songs' || selectedPage !== listPage.value || selectedKeyword !== adminSongKeyword.value) return
  adminSongs.value = data.items.map(songView); adminTotal.value = data.total
}
async function togglePublished(track) {
  await run(async () => { await request(`/admin/songs/${track.id}/publish?value=${!track.published}`, { method: 'PATCH' }); await loadAdminSongs(); notify('上下架状态已更新') })
}
async function saveSong() {
  if (!songEdit.value) return
  await run(async () => {
    const s = songEdit.value
    await request(`/admin/songs/${s.id}`, { method: 'PUT', body: {
      title: s.title, artist: s.artist, album: s.album, genre: s.category, description: s.description || '',
      vip: s.vip, published: s.published, tags: s.tagsText.split(',').map(x => x.trim()).filter(Boolean)
    } })
    songEdit.value = null; await loadAdminSongs(); notify('歌曲信息已更新')
  })
}
async function openSongEdit(track) {
  await run(async () => {
    const detail = await request(`/admin/songs/${track.id}`)
    songEdit.value = { ...songView(detail), description: detail.description || '', published: !!detail.published, tagsText: (detail.tags || []).join(', ') }
  })
}
async function uploadSong() {
  if (!uploadForm.value.audio) { notify('请选择 MP3 音频'); return }
  const { audio, cover } = uploadForm.value
  if (!/\.mp3$/i.test(audio.name) || audio.size > 50 * 1024 * 1024 || audio.size === 0) { notify('音频须为不超过 50 MB 的 MP3 文件'); return }
  if (cover && (!/\.(jpe?g|png|webp)$/i.test(cover.name) || cover.size > 10 * 1024 * 1024)) { notify('封面须为不超过 10 MB 的 JPG、PNG 或 WebP 文件'); return }
  if (audio.size + (cover?.size || 0) > 60 * 1024 * 1024) { notify('上传文件总大小不能超过 60 MB'); return }
  await run(async () => {
    const form = new FormData()
    for (const key of ['title', 'artist', 'album', 'language', 'genre', 'vip']) form.append(key, uploadForm.value[key])
    form.append('audio', uploadForm.value.audio)
    if (uploadForm.value.cover) form.append('cover', uploadForm.value.cover)
    uploadForm.value.tags.split(',').map(x => x.trim()).filter(Boolean).forEach(tag => form.append('tags', tag))
    await request('/admin/songs', { method: 'POST', body: form })
    uploadOpen.value = false; uploadForm.value = { title: '', artist: '', album: '', language: 'cn', genre: '未分类', tags: '', vip: false, audio: null, cover: null }
    await loadAdminSongs(); notify('歌曲已上传')
  })
}
async function loadAdminComments() {
  const epoch = sessionEpoch, selectedPage = listPage.value, selectedStatus = adminCommentStatus.value
  const data = await request(`/admin/comments?${queryString({ status: adminCommentStatus.value, page: listPage.value, size: 10 })}`)
  if (epoch !== sessionEpoch || page.value !== 'comments' || selectedPage !== listPage.value || selectedStatus !== adminCommentStatus.value) return
  adminComments.value = data.items; adminTotal.value = data.total
}
async function moderate(item, status) {
  await run(async () => { await request(`/admin/comments/${item.id}/status?value=${status}`, { method: 'PATCH' }); await loadAdminComments(); notify('评论状态已更新') })
}
async function rebuild() { await run(async () => { const data = await request('/admin/recommendations/rebuild', { method: 'POST' }); notify(`已为 ${data.usersProcessed} 位用户重算推荐`) }) }
async function evaluate() { await run(async () => { await request('/admin/recommendations/evaluate', { method: 'POST' }); await loadDashboard(); notify('算法实验已完成') }) }

async function loadPage() {
  if (!user.value && gatedPages.has(page.value)) return
  try {
    if (page.value === 'search') await loadSearch()
    else if (page.value === 'favorites') await loadFavorites()
    else if (page.value === 'playlists') await loadPlaylists()
    else if (page.value === 'membership') await loadVipSongs()
    else if (page.value === 'history') await loadHistory()
    else if (page.value === 'recommend') await loadRecommendations()
    else if (page.value === 'ai') await loadAi()
    else if (page.value === 'dashboard') await loadDashboard()
    else if (page.value === 'members') await loadAdminUsers()
    else if (page.value === 'songs') await loadAdminSongs()
    else if (page.value === 'comments') await loadAdminComments()
  } catch (ex) { if (ex.status !== 401) { error.value = ex.message; notify(ex.message) } }
}
function nextListPage(delta) { const next = listPage.value + delta; if (next < 1 || (delta > 0 && next * 10 - 10 >= adminTotal.value && isAdminPage.value)) return; listPage.value = next; loadPage() }
function changeFavoritesPage(delta) {
  const next = favoritesPage.value + delta
  if (next < 1 || next > Math.ceil(favoritesTotal.value / 20)) return
  favoritesPage.value = next
  loadFavorites().catch(ex => notify(ex.message))
}
function changeHistoryPage(delta) {
  const next = historyPage.value + delta
  if (next < 1 || next > Math.ceil(historyTotal.value / 20)) return
  historyPage.value = next
  loadHistory().catch(ex => notify(ex.message))
}
function changeVipPage(delta) {
  const next = vipPage.value + delta
  if (next < 1 || next > Math.ceil(vipTotal.value / 20)) return
  vipPage.value = next
  loadVipSongs().catch(ex => notify(ex.message))
}
function hashChange() {
  const target = window.location.hash.slice(1)
  const playlist = target.match(/^playlist\/(\d+)$/)
  if (playlist) {
    if (!user.value) { pendingPage.value = 'playlist-detail'; pendingPlaylistId.value = Number(playlist[1]); loginOpen.value = true }
    else if (page.value !== 'playlist-detail' || activePlaylist.value?.id !== Number(playlist[1])) openPlaylist(Number(playlist[1]))
  } else if (labels[target] && target !== page.value) navigate(target)
}
watch(page, loadPage)
watch([keyword, language, genre], () => { clearTimeout(searchTimer); searchTimer = setTimeout(() => { if (page.value === 'search') { listPage.value = 1; loadSearch() } }, 300) })
watch(favoriteKeyword, () => { clearTimeout(favoriteSearchTimer); favoriteSearchTimer = setTimeout(() => { favoritesPage.value = 1; if (page.value === 'favorites') loadFavorites().catch(ex => notify(ex.message)) }, 300) })
watch([adminUserKeyword, adminSongKeyword, adminCommentStatus], () => {
  clearTimeout(adminSearchTimer)
  adminSearchTimer = setTimeout(() => { if (isAdminPage.value) { listPage.value = 1; loadPage() } }, 300)
})
onMounted(async () => {
  window.addEventListener('shengyu:auth-expired', authExpired)
  if (getToken()) { try { user.value = await request('/auth/me') } catch { setToken('') } }
  hashChange()
  await loadHot()
  if (user.value) await Promise.all([loadFavorites(), loadFavoriteIds()])
  window.addEventListener('hashchange', hashChange)
})
onUnmounted(() => { clearTimeout(toastTimer); clearTimeout(searchTimer); clearTimeout(adminSearchTimer); clearTimeout(favoriteSearchTimer); window.removeEventListener('hashchange', hashChange); window.removeEventListener('shengyu:auth-expired', authExpired) })
</script>

<template>
  <div class="app-shell live-app" :class="{ 'admin-mode': isAdminPage }">
    <div v-if="mobileOpen" class="mobile-scrim" @click="mobileOpen = false"></div>
    <aside class="sidebar" :class="{ 'sidebar-open': mobileOpen }">
      <button class="brand" type="button" @click="navigate('home')"><span class="brand-symbol"><i></i><i></i><i></i><i></i></span><span class="brand-text">声屿<span>SHENGYU MUSIC</span></span></button>
      <div class="sidebar-scroll">
        <div class="nav-group-title">探索音乐 <span>EXPLORE</span></div>
        <nav class="side-nav"><button v-for="[id, label, icon] in userNav.slice(0, 5)" :key="id" :class="{ active: page === id }" type="button" @click="navigate(id)"><Icon :name="icon" :size="19" /><span>{{ label }}</span></button></nav>
        <div class="nav-group-title library-title">我的音乐 <span>MY LIBRARY</span></div>
        <nav class="side-nav"><button v-for="[id, label, icon] in userNav.slice(5)" :key="id" :class="{ active: page === id }" type="button" @click="navigate(id)"><Icon :name="icon" :size="19" /><span>{{ label }}</span></button></nav>
        <template v-if="isAdmin"><div class="nav-group-title admin-title">管理后台 <span>WORKSPACE</span></div><nav class="side-nav"><button v-for="[id, label, icon] in adminNav" :key="id" :class="{ active: page === id }" type="button" @click="navigate(id)"><Icon :name="icon" :size="19" /><span>{{ label }}</span></button></nav></template>
      </div>
      <button class="sidebar-profile" type="button" @click="user ? navigate('membership') : loginOpen = true"><span class="profile-avatar">{{ (user?.displayName || '访').slice(0, 1) }}</span><span class="profile-copy"><strong>{{ user?.displayName || '游客' }}</strong><small>{{ isAdmin ? '管理员' : isMember ? '会员生效中' : user ? '普通用户' : '登录后收听' }}</small></span><Icon name="chevron" :size="16" /></button>
    </aside>
    <div class="app-main">
      <header class="topbar"><div class="topbar-left"><button class="mobile-menu icon-button" type="button" @click="mobileOpen = true"><Icon name="menu" /></button><span class="breadcrumb">声屿音乐</span><span class="breadcrumb-slash">/</span><strong>{{ labels[page] }}</strong></div><div class="topbar-right"><button v-if="user" class="live-logout" type="button" @click="logout">退出登录</button><button v-else class="live-login" type="button" @click="loginOpen = true">登录 / 注册</button><button class="top-avatar" type="button" @click="user ? navigate('membership') : loginOpen = true">{{ (user?.displayName || '访').slice(0, 1) }}</button></div></header>
      <audio ref="audioElement" :src="audioUrl" preload="metadata" class="live-hidden-audio" @play="playing = true" @pause="playing = false" @loadedmetadata="playbackDuration = audioElement?.duration || 0" @timeupdate="onTimeUpdate" @ended="onEnded" @error="audioError"></audio>
      <main class="content">
        <div v-if="error" class="live-error">{{ error }} <button type="button" @click="error = ''; loadPage()">重试</button></div>

        <template v-if="page === 'home'">
          <section class="hero-card"><div class="hero-copy"><span class="eyebrow light"><span class="eyebrow-line"></span> YOUR DAILY SOUNDTRACK</span><h1>让音乐<br /><em>找到你。</em></h1><p>在每一个值得被记住的瞬间，遇见一首刚好懂你的歌。</p><button class="button button-light" type="button" @click="navigate('recommend')">探索今日推荐 <Icon name="arrow" :size="18" /></button><div class="hero-counter"><strong>01 / 01</strong><span></span><small>每日精选</small></div></div><div class="hero-art"><div class="hero-vinyl"><span></span></div><div class="hero-art-cover"><AlbumCover :track="hot[0] || { title: '声屿音乐', tone: 'cover-sunset' }" size="hero" show-title /></div></div></section>
          <div class="section-heading"><div><span class="eyebrow">MADE FOR YOU</span><h2>今天，听点不一样的</h2></div></div><div class="feature-grid"><button class="feature-card feature-daily" type="button" @click="navigate('recommend')"><span class="feature-icon"><Icon name="spark" /></span><span class="feature-copy"><small>每日更新 · 为你挑选</small><strong>每日推荐</strong><span>从你喜欢的旋律出发，发现更多可能。</span></span><Icon name="arrow" :size="20" class="feature-arrow" /></button><button class="feature-card feature-ai" type="button" @click="navigate('ai')"><span class="feature-icon"><Icon name="spark" /></span><span class="feature-ai-quote">“想听适合雨天独处的<br />温柔中文歌”</span><span class="feature-copy"><small>描述心情 · 发现好歌</small><strong>AI 灵感查歌</strong><span>说出你的心情，让音乐接住你。</span></span><Icon name="arrow" :size="20" class="feature-arrow" /></button></div><div class="section-heading compact"><div><span class="eyebrow">TRENDING NOW</span><h2>正在被大家喜欢</h2></div><button class="text-link" type="button" @click="navigate('search')">探索曲库 <Icon name="arrow" :size="17" /></button></div><TrackTable :tracks="hot.slice(0, 8)" show-plays @select="track => playSong(track, hot.slice(0, 8))" /><section v-for="section in languageSections" :key="section.code" class="home-language-section"><div class="section-heading compact"><div><span class="eyebrow">{{ section.subtitle }}</span><h2>{{ section.title }}</h2></div><button class="text-link" type="button" @click="language = section.code; navigate('search'); loadSearch()">查看全部 <Icon name="arrow" :size="17" /></button></div><TrackTable :tracks="section.tracks" @select="track => playSong(track, section.tracks)" /></section><section v-if="genreOptions.length" class="home-genres"><div class="section-heading compact"><div><span class="eyebrow">FIND YOUR SOUND</span><h2>按风格发现</h2></div></div><div class="genre-card-grid"><button v-for="item in genreOptions" :key="item.genre" type="button" @click="genre = item.genre; language = ''; navigate('search')"><span>♪</span><strong>{{ genreNames[item.genre] || item.genre }}</strong><small>{{ item.song_count }} 首歌曲</small><Icon name="arrow" :size="17" /></button></div></section><div v-if="!hot.length" class="empty-state">曲库暂无已上架歌曲。管理员可先导入本地音乐。</div>
        </template>

        <template v-else-if="page === 'search'"><div class="page-intro"><span class="eyebrow">EXPLORE THE SOUND</span><h1>找到下一首<span class="accent-dot">.</span></h1><p>浏览曲库中真实上架的歌曲。</p></div><div class="search-panel"><Icon name="search" :size="23" /><input v-model="keyword" type="search" placeholder="搜索歌曲、歌手或专辑" aria-label="搜索歌曲" /><select v-model="language" aria-label="筛选语言"><option value="">全部语言</option><option value="cn">中文</option><option value="en">英文</option><option value="jp">日文</option><option value="fr">法文</option><option value="ru">俄文</option></select><select v-model="genre" aria-label="筛选曲风"><option value="">全部曲风</option><option v-for="item in genreOptions" :key="item.genre" :value="item.genre">{{ genreNames[item.genre] || item.genre }}（{{ item.song_count }}）</option></select></div><div class="section-heading"><div><span class="eyebrow">ALL TRACKS</span><h2>曲库歌曲 <sup>{{ total }}</sup></h2></div><span class="subtle-text">第 {{ listPage }} 页</span></div><TrackTable :tracks="songs" @select="track => playSong(track, songs)" /><p v-if="searchLoading" class="live-note">正在搜索…</p><div v-if="!searchLoading && !songs.length" class="empty-state">没有找到歌曲，试试其他关键词。</div><div class="live-pagination"><button :disabled="listPage === 1" @click="listPage--; loadSearch()">上一页</button><span>{{ listPage }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span><button :disabled="listPage * 20 >= total" @click="listPage++; loadSearch()">下一页</button></div></template>

        <template v-else-if="page === 'player'"><div v-if="current" class="player-stage"><div class="player-artwork"><div class="player-back-disc"></div><AlbumCover :track="current" size="player" show-title /></div><div class="player-content"><span class="eyebrow"><span class="live-dot"></span> NOW PLAYING</span><div class="player-meta"><span class="music-type">{{ current.genreName }} · {{ current.album }}</span><h1>{{ current.title }}</h1><p>{{ current.artist }}</p></div><div class="player-tags"><span v-if="current.vip"><Icon name="crown" :size="14" /> {{ isMember || isAdmin ? '会员完整播放' : '30 秒试听' }}</span><span>{{ current.duration }}</span></div><p v-if="audioLoading" class="live-note">正在加载音频…</p><div v-if="playbackError" class="live-error">{{ playbackError }} <button type="button" @click="loadAudio(current)">重试</button></div><div class="live-player-controls"><button class="queue-control" type="button" aria-label="上一首" title="上一首" @click="skipSong(-1)"><Icon name="prev" :size="21" /></button><button class="play-main" type="button" @click="togglePlaybackWithHint" title="播放或暂停"><Icon :name="playing ? 'pause' : 'play'" :size="24" /></button><button class="queue-control" type="button" aria-label="下一首" title="下一首" @click="skipSong(1)"><Icon name="next" :size="21" /></button><input type="range" min="0" :max="playbackDuration" :value="progress" @input="seekAudio" /><span>{{ formatTime(progress) }} / {{ formatTime(playbackDuration) }}</span></div><div class="player-actions"><button type="button" :class="{ 'is-favorite': favoriteIds.has(current.id) }" :aria-pressed="favoriteIds.has(current.id)" :disabled="favoritePending.has(current.id)" @click="toggleFavorite(current)"><Icon name="heart" :size="18" /> {{ favoriteIds.has(current.id) ? '取消收藏' : '收藏歌曲' }}</button><button class="playlist-add-button" type="button" @click="openPlaylistPicker(current)"><Icon name="plus" :size="18" /> 加入歌单</button></div></div></div><div v-else class="empty-state">从曲库选择一首歌开始播放。</div><section v-if="current" class="live-comments"><div class="section-heading"><div><span class="eyebrow">LISTENER NOTES</span><h2>听众评论</h2></div></div><form class="live-inline-form" @submit.prevent="submitComment"><input v-model="commentText" maxlength="1000" placeholder="写下听这首歌时的心情…" /><button class="button button-accent" type="submit">发表评论</button></form><p class="live-note">评论经管理员审核后公开。</p><p v-if="!songComments.length" class="live-note">暂无已审核的评论。</p><div v-for="comment in songComments" :key="comment.id" class="live-comment"><strong>{{ comment.display_name }}</strong><span>{{ comment.created_at }}</span><p>{{ comment.content }}</p></div></section></template>

        <template v-else-if="page === 'recommend'"><section class="recommend-hero"><div><span class="eyebrow light">CURATED JUST FOR YOU</span><h1>每日推荐<span> / 01</span></h1><p>从你的收听足迹里，打捞今天的好心情。</p></div><div class="recommend-visual"><div class="recommend-circle c1"></div><div class="recommend-circle c2"></div><span>YOU<br />+<br />MUSIC</span></div></section><div class="section-heading"><div><span class="eyebrow">THE LIST</span><h2>今日为你精选</h2></div><span class="subtle-text">根据昨日收听每日更新</span></div><TrackTable :tracks="recommendations" show-reason @select="track => playSong(track, recommendations)" /><div v-if="!recommendations.length" class="empty-state">曲库暂无可推荐歌曲。</div></template>

        <template v-else-if="page === 'ai'"><div class="ai-layout"><section class="ai-main"><div class="ai-heading"><span class="ai-avatar"><Icon name="spark" :size="25" /></span><div><span class="eyebrow">YOUR MUSIC COMPANION</span><h1>把心情，说给音乐听<span class="accent-dot">.</span></h1><p>可以连续追问，结果只来自本地曲库。</p></div></div><div class="live-chat-scroll"><div v-if="!messages.length" class="ai-welcome">描述一个场景、一种感觉，或一句模糊的记忆。<br />例如：“适合雨天独处的温柔中文歌”。</div><div v-for="msg in messages" :key="msg.id" class="chat-message" :class="msg.role === 'USER' ? 'user-message' : 'ai-message'"><template v-if="msg.role === 'ASSISTANT'"><span class="ai-message-icon"><Icon name="spark" :size="16" /></span><div><p>{{ msg.content }}</p><div v-if="msg.songs?.length" class="ai-results"><button v-for="song in msg.songs" :key="song.id" @click="playSong(song, msg.songs)"><AlbumCover :track="song" size="tiny" /><span><strong>{{ song.title }}</strong><small>{{ song.artist }}</small></span><Icon name="play" :size="17" /></button></div></div></template><template v-else>{{ msg.content }}</template></div></div><form class="chat-input" @submit.prevent="sendChat"><input v-model="chatText" maxlength="500" placeholder="继续描述你想听的音乐…" /><button type="submit" :disabled="busy"><Icon name="send" :size="18" /></button></form><p class="ai-disclaimer">由 DeepSeek 理解描述 · 由本地曲库返回结果</p></section><aside class="ai-side"><div class="ai-side-card"><span class="eyebrow">YOUR CONVERSATIONS</span><h3>对话记录</h3><button type="button" @click="newConversation">+ 开始新对话</button><button v-for="item in conversations" :key="item.id" type="button" @click="openConversation(item.id)">{{ item.title }} <Icon name="chevron" :size="15" /></button></div></aside></div></template>

        <template v-else-if="page === 'favorites'">
  <div class="page-intro"><span class="eyebrow">YOUR COLLECTION</span><h1>我的收藏<span class="accent-dot">.</span></h1><p>喜欢的音乐，永远有位置。</p></div>
  <div class="search-panel favorite-search"><Icon name="search" :size="21" /><input v-model="favoriteKeyword" type="search" placeholder="搜索收藏的歌曲、歌手或专辑" aria-label="搜索收藏" /></div>
  <TrackTable :tracks="favorites" @select="track => playSong(track, favorites)" />
  <p v-if="favoritesLoading" class="live-note">正在加载收藏…</p>
  <div v-if="favoritesTotal > 20" class="live-pagination"><button :disabled="favoritesPage === 1 || favoritesLoading" @click="changeFavoritesPage(-1)">上一页</button><span>{{ favoritesPage }} / {{ Math.ceil(favoritesTotal / 20) }}</span><button :disabled="favoritesPage * 20 >= favoritesTotal || favoritesLoading" @click="changeFavoritesPage(1)">下一页</button></div>
  <div v-if="!favoritesLoading && !favorites.length" class="empty-state">{{ favoriteKeyword ? '收藏中没有匹配的歌曲。' : '还没有收藏歌曲，去曲库发现第一首吧。' }}</div>
</template>

        <template v-else-if="page === 'playlists'">
  <div class="page-intro"><span class="eyebrow">MADE BY YOU</span><h1>我的歌单<span class="accent-dot">.</span></h1><p>每张歌单，都是一段专属的音乐旅程。</p></div>
  <form class="live-inline-form playlist-create-form" @submit.prevent="createPlaylist"><input v-model="playlistName" maxlength="100" placeholder="给新歌单起一个名字" /><button class="button button-accent" type="submit"><Icon name="plus" :size="18" /> 创建歌单</button></form>
  <div class="live-playlist-grid"><button v-for="(list, index) in playlists" :key="list.id" class="live-playlist-card" type="button" @click="openPlaylist(list.id)"><span class="playlist-card-art"><img v-if="list.cover_song_id" :src="`/api/songs/${list.cover_song_id}/cover`" alt="" loading="lazy" @error="$event.target.style.display = 'none'" /><span>♫</span></span><span class="playlist-card-caption">PLAYLIST / {{ String(index + 1).padStart(2, '0') }}</span><strong>{{ list.name }}</strong><small>{{ list.song_count }} 首歌曲 <Icon name="arrow" :size="16" /></small></button></div>
  <p v-if="!playlists.length" class="empty-state">还没有歌单，创建一个收藏喜欢的歌曲吧。</p>
</template>
<template v-else-if="page === 'playlist-detail'">
  <button class="playlist-back" type="button" @click="navigate('playlists')">← 返回我的歌单</button>
  <template v-if="activePlaylist">
    <section class="playlist-detail-hero"><div class="playlist-detail-cover"><AlbumCover :track="activePlaylist.songs[0] || { title: activePlaylist.name, tone: 'cover-sunset' }" size="player" show-title /></div><div><span class="eyebrow">YOUR PLAYLIST</span><h1>{{ activePlaylist.name }}</h1><p>{{ activePlaylist.songs.length }} 首歌曲 · 由你亲自收藏</p><button class="button button-accent" type="button" :disabled="!activePlaylist.songs.length" @click="playSong(activePlaylist.songs[0], activePlaylist.songs)"><Icon name="play" :size="18" /> 播放歌单</button></div></section>
    <div class="playlist-detail-actions"><form class="live-inline-form" @submit.prevent="renamePlaylist"><input v-model="playlistRename" maxlength="100" aria-label="歌单名称" /><button class="button button-accent" type="submit">保存名称</button></form><button class="button live-delete" type="button" @click="deletePlaylist">删除歌单</button></div>
    <div v-if="!activePlaylist.songs.length" class="empty-state">歌单还没有歌曲。播放歌曲时点击“加入歌单”即可添加。</div>
    <div v-for="(song, index) in activePlaylist.songs" :key="song.id" class="playlist-song-row"><button type="button" @click="playSong(song, activePlaylist.songs)"><span>{{ String(index + 1).padStart(2, '0') }}</span><AlbumCover :track="song" size="tiny" /><span><strong>{{ song.title }}</strong><small>{{ song.artist }} · {{ song.genreName }}</small></span></button><button type="button" class="playlist-remove" :aria-label="`从歌单移除 ${song.title}`" @click="removeFromPlaylist(song.id)">移除</button></div>
  </template>
  <div v-else class="empty-state">正在加载歌单…</div>
</template>

        <template v-else-if="page === 'history'"><div class="page-intro"><span class="eyebrow">YOUR LISTENING JOURNEY</span><h1>播放历史<span class="accent-dot">.</span></h1><p>每一首，都曾陪你度过一个时刻。</p></div><TrackTable :tracks="history" show-date @select="track => playSong(track, history)" /><p v-if="historyLoading" class="live-note">正在加载播放历史…</p><div v-if="historyTotal > 20" class="live-pagination"><button :disabled="historyPage === 1 || historyLoading" @click="changeHistoryPage(-1)">上一页</button><span>{{ historyPage }} / {{ Math.ceil(historyTotal / 20) }}</span><button :disabled="historyPage * 20 >= historyTotal || historyLoading" @click="changeHistoryPage(1)">下一页</button></div><div v-if="!historyLoading && !history.length" class="empty-state">还没有播放记录。完整收听至少 10 秒后会保存。</div></template>

        <template v-else-if="page === 'membership'"><section class="membership-hero"><div><span class="eyebrow light"><Icon name="crown" :size="17" /> SHENGYU PLUS</span><h1>让每一首喜欢的歌，<br /><em>都能完整抵达。</em></h1><p>解锁会员歌曲完整播放，音乐不必停在前奏。</p><div class="membership-status"><span class="status-signal"></span>{{ isMember ? '会员生效中' : '当前为普通用户' }}<span v-if="isMember"> · 有效期至 {{ String(user.membershipExpiresAt).slice(0, 10) }}</span></div></div><div class="member-mark"><span>声屿</span><strong>PLUS</strong><small>MEMBERSHIP</small></div></section><div class="section-heading"><div><span class="eyebrow">YOUR BENEFITS</span><h2>聆听特权</h2></div></div><div class="benefit-grid"><div><Icon name="disc" :size="28" /><strong>完整播放</strong><p>会员歌曲，从第一秒听到最后一秒。</p></div><div><Icon name="music" :size="28" /><strong>更多好歌</strong><p>探索曲库中的会员内容。</p></div><div><Icon name="headphones" :size="28" /><strong>有效期管理</strong><p>会员资格由管理员开通与续期。</p></div></div><div class="section-heading compact"><div><span class="eyebrow">MEMBERS ONLY</span><h2>会员专享歌曲 <sup>{{ vipTotal }}</sup></h2></div><span class="subtle-text">普通用户可试听 30 秒</span></div><TrackTable :tracks="vipSongs" @select="track => playSong(track, vipSongs)" /><p v-if="vipLoading" class="live-note">正在加载会员歌曲…</p><div v-if="!vipLoading && !vipSongs.length" class="empty-state">暂无上架的会员歌曲。</div><div v-if="vipTotal > 20" class="live-pagination"><button :disabled="vipPage === 1 || vipLoading" @click="changeVipPage(-1)">上一页</button><span>{{ vipPage }} / {{ Math.ceil(vipTotal / 20) }}</span><button :disabled="vipPage * 20 >= vipTotal || vipLoading" @click="changeVipPage(1)">下一页</button></div></template>

        <template v-else-if="page === 'dashboard'"><div class="page-intro admin-intro"><span class="eyebrow">OVERVIEW</span><h1>数据概览<span class="accent-dot">.</span></h1><p>业务数据来自当前系统。</p></div><div v-if="dashboard" class="metric-grid"><div v-for="[label, value, icon] in [['累计用户', dashboard.users, 'users'], ['有效会员', dashboard.members, 'crown'], ['上架歌曲', dashboard.songs, 'music'], ['今日播放', dashboard.playsToday, 'headphones']]" :key="label" class="metric-card"><span>{{ label }} <Icon :name="icon" :size="19" /></span><strong>{{ value }}</strong></div></div><DashboardCharts v-if="dashboard" :dashboard="dashboard" /><div class="dashboard-grid"><div class="dashboard-panel"><div class="panel-heading"><h3>热门歌曲</h3></div><div v-for="song in dashboard?.topSongs || []" :key="song.id" class="mini-track"><AlbumCover :track="songView(song)" size="tiny" /><div><strong>{{ song.title }}</strong><small>{{ song.artist }}</small></div><b>{{ song.play_count }} 次播放</b></div></div><div class="dashboard-panel experiment-panel"><div class="panel-heading"><h3>算法实验</h3></div><div v-for="item in dashboard?.experiments || []" :key="item.algorithm + item.created_at" class="live-experiment"><strong>{{ item.algorithm }}</strong><span>Precision@10: {{ Number(item.precision_at_10).toFixed(3) }}</span><span>Recall@10: {{ Number(item.recall_at_10).toFixed(3) }}</span><span>Coverage: {{ Number(item.coverage).toFixed(3) }}</span></div><p v-if="!dashboard?.experiments?.length">尚未运行离线实验。</p><button class="outline-button" @click="evaluate">运行公开数据集实验</button><button class="outline-button" @click="rebuild">立即重算用户推荐</button></div></div></template>

        <template v-else-if="page === 'members'"><div class="page-intro admin-intro"><span class="eyebrow">PEOPLE & MEMBERSHIP</span><h1>用户与会员<span class="accent-dot">.</span></h1><p>设置会员有效期并管理账号状态。</p></div><div class="admin-list-panel"><div class="admin-filter-row"><input v-model="adminUserKeyword" type="search" placeholder="搜索用户名或昵称" aria-label="搜索用户" /></div><div class="panel-heading list-heading"><h3>用户列表 <sup>{{ adminTotal }}</sup></h3></div><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>用户</th><th>账号</th><th>角色</th><th>会员到期时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in adminUsers" :key="item.id"><td>{{ item.display_name }}</td><td>{{ item.username }}</td><td>{{ item.role }}</td><td><input v-model="membershipDraft[item.id]" type="datetime-local" class="table-input" :disabled="item.role === 'ADMIN'" /></td><td>{{ item.enabled ? '正常' : '禁用' }}</td><td><button class="table-action" :disabled="item.role === 'ADMIN'" @click="saveMembership(item)">保存会员</button><button class="table-action" :disabled="item.role === 'ADMIN'" @click="toggleUser(item)">{{ item.enabled ? '禁用' : '启用' }}</button></td></tr></tbody></table></div><p v-if="!adminUsers.length" class="empty-state">没有符合条件的用户。</p><div class="pagination"><span>共 {{ adminTotal }} 位用户</span><div><button :disabled="listPage === 1" @click="nextListPage(-1)">上一页</button><button :disabled="listPage * 10 >= adminTotal" @click="nextListPage(1)">下一页</button></div></div></div></template>

        <template v-else-if="page === 'songs'"><div class="page-intro with-action admin-intro"><div><span class="eyebrow">MUSIC CATALOG</span><h1>歌曲管理<span class="accent-dot">.</span></h1><p>上传歌曲、编辑标签与上下架。</p></div><button class="button button-accent" @click="uploadOpen = true"><Icon name="upload" :size="18" /> 上传歌曲</button></div><div class="admin-list-panel"><div class="admin-filter-row"><input v-model="adminSongKeyword" type="search" placeholder="搜索歌曲或歌手" aria-label="搜索管理歌曲" /></div><div class="panel-heading list-heading"><h3>全部歌曲 <sup>{{ adminTotal }}</sup></h3></div><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>歌曲</th><th>专辑 / 风格</th><th>会员</th><th>播放量</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="song in adminSongs" :key="song.id"><td><span class="song-cell"><AlbumCover :track="song" size="tiny" /><span><strong>{{ song.title }}</strong><small>{{ song.artist }}</small></span></span></td><td>{{ song.album }} / {{ song.category }}</td><td>{{ song.vip ? 'VIP' : '免费' }}</td><td>{{ song.plays }}</td><td>{{ song.published ? '已上架' : '已下架' }}</td><td><button class="table-action" @click="openSongEdit(song)">编辑</button><button class="table-action" @click="togglePublished(song)">{{ song.published ? '下架' : '上架' }}</button></td></tr></tbody></table></div><p v-if="!adminSongs.length" class="empty-state">没有符合条件的歌曲。</p><div class="pagination"><span>共 {{ adminTotal }} 首歌曲</span><div><button :disabled="listPage === 1" @click="nextListPage(-1)">上一页</button><button :disabled="listPage * 10 >= adminTotal" @click="nextListPage(1)">下一页</button></div></div></div></template>

        <template v-else-if="page === 'comments'"><div class="page-intro admin-intro"><span class="eyebrow">COMMUNITY VOICES</span><h1>评论管理<span class="accent-dot">.</span></h1><p>审核听众的音乐分享。</p></div><div class="admin-filter-row"><select v-model="adminCommentStatus" aria-label="筛选评论状态"><option value="">全部状态</option><option value="PENDING">待审核</option><option value="APPROVED">已通过</option><option value="HIDDEN">已隐藏</option></select></div><div class="comment-list"><p v-if="!adminComments.length" class="empty-state">当前筛选条件下没有评论。</p><div v-for="item in adminComments" :key="item.id" class="comment-card"><span class="list-avatar avatar-peach">{{ item.display_name?.slice(0, 1) }}</span><div class="comment-body"><div class="comment-meta"><strong>{{ item.display_name }}</strong><span>评论《{{ item.song_title }}》</span><span>{{ item.created_at }}</span></div><p>{{ item.content }}</p><span class="status-pill" :class="item.status === 'APPROVED' ? 'success' : 'pending'">{{ item.status }}</span></div><div class="comment-actions"><button @click="moderate(item, 'APPROVED')">通过</button><button @click="moderate(item, 'HIDDEN')">隐藏</button></div></div></div><div class="live-pagination"><button :disabled="listPage === 1" @click="nextListPage(-1)">上一页</button><span>第 {{ listPage }} 页 · 共 {{ adminTotal }} 条</span><button :disabled="listPage * 10 >= adminTotal" @click="nextListPage(1)">下一页</button></div></template>
      </main>
      <footer class="footer-player"><div class="footer-track"><AlbumCover :track="current || { title: '声屿音乐', tone: 'cover-sunset' }" size="tiny" /><div><strong>{{ current?.title || '尚未播放' }}</strong><small>{{ current?.artist || '去发现一首好歌' }}</small></div></div><div class="footer-center"><div class="footer-controls"><button type="button" aria-label="上一首" title="上一首" @click="skipSong(-1)"><Icon name="prev" :size="19" /></button><button class="footer-play" type="button" @click="togglePlaybackWithHint" title="播放或暂停"><Icon :name="playing ? 'pause' : 'play'" :size="17" /></button><button type="button" aria-label="下一首" title="下一首" @click="skipSong(1)"><Icon name="next" :size="19" /></button><button type="button" :class="{ active: playMode !== 'LIST' }" :aria-label="playModeLabel" :title="playModeLabel" @click="cyclePlayMode"><Icon name="repeat" :size="18" /><small v-if="playMode === 'ONE'">1</small></button></div><div class="footer-progress"><span>{{ Math.floor(progress / 60).toString().padStart(2, '0') }}:{{ (Math.floor(progress) % 60).toString().padStart(2, '0') }}</span><div><i :style="{ width: audioElement?.duration ? `${progress / audioElement.duration * 100}%` : '0%' }"></i></div><span>{{ formatTime(playbackDuration) }}</span></div></div><div class="footer-tools"><button type="button" aria-label="播放列表" :aria-expanded="queueOpen" @click="showQueue" title="打开或关闭播放列表"><Icon name="list" :size="20" /></button><button @click="showPlayer" title="回到歌曲页面" aria-label="回到歌曲页面"><Icon name="disc" :size="20" /></button></div></footer><div v-if="queueOpen" class="live-queue-panel"><div class="panel-heading"><h3>播放列表 · {{ playModeLabel }}</h3><button type="button" @click="showQueue">关闭</button></div><button v-for="(song, index) in playQueue" :key="`${song.id}-${index}`" type="button" :class="{ active: index === queueIndex }" @click="playSong(song)">{{ index + 1 }}. {{ song.title }} · {{ song.artist }}</button><p v-if="!playQueue.length" class="live-note">暂无待播歌曲</p></div>
    </div>

    <div v-if="playlistPickerOpen" class="live-modal-backdrop" @click.self="playlistPickerOpen = false"><div class="live-modal playlist-picker"><button class="modal-close" type="button" @click="playlistPickerOpen = false">×</button><span class="eyebrow">ADD TO PLAYLIST</span><h2>将《{{ playlistPickerTrack?.title }}》加入歌单</h2><p class="live-note">选择一个已有歌单，歌曲会立即加入。</p><button v-for="list in playlists" :key="list.id" class="playlist-picker-option" type="button" @click="addToPlaylist(list.id)"><span>♫</span><strong>{{ list.name }}</strong><small>{{ list.song_count }} 首</small><Icon name="plus" :size="18" /></button><p v-if="!playlists.length" class="empty-state">还没有歌单。请先到“我的歌单”创建一个。</p><button v-if="!playlists.length" class="button button-accent" type="button" @click="playlistPickerOpen = false; navigate('playlists')">去创建歌单</button></div></div>
<div v-if="loginOpen" class="live-modal-backdrop" @click.self="loginOpen = false"><form class="live-modal auth-modal" @submit.prevent="login"><button class="modal-close" type="button" @click="loginOpen = false">×</button><span class="eyebrow">WELCOME TO SHENGYU</span><h2>{{ authMode === 'login' ? '再次相遇，好音乐。' : '开始你的音乐旅程。' }}</h2><label>用户名<input v-model="loginName" required minlength="3" maxlength="30" autocomplete="username" /></label><label v-if="authMode === 'register'">昵称<input v-model="registerName" maxlength="80" /></label><label>密码<input v-model="loginPassword" required type="password" minlength="8" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" /></label><button class="button button-accent" type="submit" :disabled="busy">{{ authMode === 'login' ? '登录' : '注册' }}</button><button class="auth-switch" type="button" @click="authMode = authMode === 'login' ? 'register' : 'login'">{{ authMode === 'login' ? '没有账号？立即注册' : '已有账号？返回登录' }}</button></form></div>
    <div v-if="uploadOpen" class="live-modal-backdrop" @click.self="uploadOpen = false"><form class="live-modal" @submit.prevent="uploadSong"><button class="modal-close" type="button" @click="uploadOpen = false">×</button><span class="eyebrow">MUSIC CATALOG</span><h2>上传新歌曲</h2><div class="form-grid"><label>歌名<input v-model="uploadForm.title" required maxlength="200" /></label><label>歌手<input v-model="uploadForm.artist" required maxlength="200" /></label><label>专辑<input v-model="uploadForm.album" /></label><label>语言<select v-model="uploadForm.language"><option value="cn">中文</option><option value="en">英文</option><option value="jp">日文</option><option value="fr">法文</option><option value="ru">俄文</option></select></label><label>风格<input v-model="uploadForm.genre" /></label><label>标签，逗号分隔<input v-model="uploadForm.tags" /></label><label>MP3 音频<input type="file" accept=".mp3,audio/mpeg" required @change="uploadForm.audio = $event.target.files[0]" /></label><label>封面<input type="file" accept=".jpg,.jpeg,.png,.webp,image/*" @change="uploadForm.cover = $event.target.files[0]" /></label></div><label class="checkbox-line"><input v-model="uploadForm.vip" type="checkbox" /> 会员歌曲（需要 FFmpeg 生成试听文件）</label><button class="button button-accent" type="submit" :disabled="busy">保存歌曲</button></form></div>
    <div v-if="songEdit" class="live-modal-backdrop" @click.self="songEdit = null"><form class="live-modal" @submit.prevent="saveSong"><button class="modal-close" type="button" @click="songEdit = null">×</button><span class="eyebrow">EDIT TRACK</span><h2>编辑歌曲</h2><div class="form-grid"><label>歌名<input v-model="songEdit.title" required /></label><label>歌手<input v-model="songEdit.artist" required /></label><label>专辑<input v-model="songEdit.album" /></label><label>风格<input v-model="songEdit.category" /></label><label>标签，逗号分隔<input v-model="songEdit.tagsText" /></label><label>描述<input v-model="songEdit.description" /></label></div><label class="checkbox-line"><input v-model="songEdit.vip" type="checkbox" /> 会员歌曲</label><label class="checkbox-line"><input v-model="songEdit.published" type="checkbox" /> 已上架</label><button class="button button-accent" type="submit" :disabled="busy">保存修改</button></form></div>
    <div v-if="toast" class="toast-message">{{ toast }}</div>
  </div>
</template>
