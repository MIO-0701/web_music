const BASE = import.meta.env.VITE_API_BASE || '/api'
let token = localStorage.getItem('shengyu_token') || ''

export function setToken(value) {
  token = value || ''
  if (token) localStorage.setItem('shengyu_token', token)
  else localStorage.removeItem('shengyu_token')
}
export function getToken() { return token }

export async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const body = options.body instanceof FormData ? options.body : options.body == null ? undefined : JSON.stringify(options.body)
  if (body && !(body instanceof FormData)) headers['Content-Type'] = 'application/json'
  let response
  try { response = await fetch(`${BASE}${path}`, { ...options, body, headers }) }
  catch (error) {
    if (error.name === 'AbortError') throw error
    throw new Error('无法连接后端服务，请检查 Docker 与 Spring Boot 是否启动')
  }
  if (!response.ok) {
    let message = `请求失败（${response.status}）`
    try { message = (await response.json()).error || message } catch { /* no JSON body */ }
    if (response.status === 401 && token) {
      setToken('')
      window.dispatchEvent(new Event('shengyu:auth-expired'))
    }
    const error = new Error(message)
    error.status = response.status
    throw error
  }
  if (options.raw) return response
  if (response.status === 204) return null
  return response.json()
}

export function queryString(params) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== '') search.set(key, value) })
  return search.toString()
}

const tones = ['cover-sunset', 'cover-sea', 'cover-night', 'cover-orange', 'cover-green', 'cover-cloud', 'cover-light', 'cover-space']
export const genreNames = { pop: '流行', rock: '摇滚', alternative: '另类', 'rnb-soul': 'R&B / 灵魂', 'hip-hop': '嘻哈 / 说唱', electronic: '电子', dance: '舞曲', jazz: '爵士', classical: '古典', folk: '民谣', 'singer-songwriter': '唱作', soundtrack: '原声 / 配乐', anime: '动漫音乐', country: '乡村', metal: '金属', blues: '蓝调', reggae: '雷鬼', latin: '拉丁', instrumental: '器乐', 'new-age': '新世纪' }
export function songView(row) {
  const id = Number(row.id ?? row.song_id)
  const duration = Number(row.duration_seconds ?? (row.duration ? Number(row.duration.split(':')[0]) * 60 + Number(row.duration.split(':')[1]) : 0))
  return {
    ...row, id,
    title: row.title,
    artist: row.artist,
    album: row.album_name || row.album || '未知专辑',
    category: row.genre || row.category || '未分类',
    genreName: genreNames[row.genre || row.category] || row.genre || row.category || '未分类',
    duration: `${String(Math.floor(duration / 60)).padStart(2, '0')}:${String(duration % 60).padStart(2, '0')}`,
    tone: row.tone || tones[id % tones.length],
    vip: !!row.vip,
    plays: String(row.play_count ?? row.plays ?? 0),
    date: row.created_at ? String(row.created_at).slice(0, 10) : '',
    coverUrl: row.coverUrl?.startsWith('http') ? row.coverUrl : row.coverUrl ? `${BASE.replace(/\/api$/, '')}${row.coverUrl}` : `${BASE}/songs/${id}/cover`
  }
}
