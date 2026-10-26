<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import Icon from './Icon.vue'
import AlbumCover from './AlbumCover.vue'
import TrackTable from './TrackTable.vue'
import { tracks, playlists, comments, members } from './data'

const userNav = [
  { id: 'home', label: '发现音乐', icon: 'home' },
  { id: 'search', label: '曲库检索', icon: 'search' },
  { id: 'player', label: '播放页面', icon: 'disc' },
  { id: 'recommend', label: '每日推荐', icon: 'spark' },
  { id: 'ai', label: 'AI 查歌', icon: 'spark' },
  { id: 'favorites', label: '我的收藏', icon: 'heart' },
  { id: 'playlists', label: '我的歌单', icon: 'list' },
  { id: 'history', label: '播放历史', icon: 'history' },
  { id: 'membership', label: '会员中心', icon: 'crown' }
]
const adminNav = [
  { id: 'dashboard', label: '数据概览', icon: 'chart' },
  { id: 'members', label: '用户与会员', icon: 'users' },
  { id: 'songs', label: '歌曲管理', icon: 'music' },
  { id: 'comments', label: '评论管理', icon: 'comment' }
]
const titles = {
  home: '发现音乐', search: '曲库检索', player: '正在播放', recommend: '每日推荐', ai: 'AI 查歌',
  favorites: '我的收藏', playlists: '我的歌单', history: '播放历史', membership: '会员中心',
  dashboard: '数据概览', members: '用户与会员', songs: '歌曲管理', comments: '评论管理'
}
const page = ref('home')
const isAdmin = computed(() => adminNav.some(item => item.id === page.value))
const mobileOpen = ref(false)
const query = ref('')
const category = ref('全部')
const currentTrack = ref(tracks[0])
const playing = ref(false)
const toast = ref('')
let toastTimer

const categories = ['全部', '流行', '民谣', '独立摇滚', '梦幻流行']
const filteredTracks = computed(() => tracks.filter(track => {
  const matchesQuery = !query.value || `${track.title} ${track.artist} ${track.album} ${track.category}`.toLowerCase().includes(query.value.toLowerCase())
  const matchesCategory = category.value === '全部' || track.category.includes(category.value)
  return matchesQuery && matchesCategory
}))

function setPage(id) {
  page.value = id
  mobileOpen.value = false
  window.location.hash = id
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
function syncHash() {
  const hash = window.location.hash.slice(1)
  if (titles[hash]) page.value = hash
}
function selectTrack(track) {
  currentTrack.value = track
  playing.value = false
  setPage('player')
}
function notice(message = '此处为静态原型，功能将在后续阶段接入') {
  toast.value = message
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { toast.value = '' }, 3000)
}
onMounted(() => { syncHash(); window.addEventListener('hashchange', syncHash) })
onUnmounted(() => { window.removeEventListener('hashchange', syncHash); clearTimeout(toastTimer) })
</script>

<template>
  <div class="app-shell" :class="{ 'admin-mode': isAdmin }">
    <div v-if="mobileOpen" class="mobile-scrim" @click="mobileOpen = false"></div>
    <aside class="sidebar" :class="{ 'sidebar-open': mobileOpen }">
      <button class="brand" type="button" @click="setPage('home')" aria-label="回到声屿首页">
        <span class="brand-symbol"><i></i><i></i><i></i><i></i></span>
        <span class="brand-text">声屿<span>SHENGYU MUSIC</span></span>
      </button>
      <div class="sidebar-scroll">
        <div class="nav-group-title">探索音乐 <span>EXPLORE</span></div>
        <nav class="side-nav" aria-label="用户页面">
          <button v-for="item in userNav.slice(0, 5)" :key="item.id" type="button" :class="{ active: page === item.id }" @click="setPage(item.id)"><Icon :name="item.icon" :size="19" /><span>{{ item.label }}</span><span v-if="item.id === 'ai'" class="nav-new">NEW</span></button>
        </nav>
        <div class="nav-group-title library-title">我的音乐 <span>MY LIBRARY</span></div>
        <nav class="side-nav" aria-label="我的音乐">
          <button v-for="item in userNav.slice(5)" :key="item.id" type="button" :class="{ active: page === item.id }" @click="setPage(item.id)"><Icon :name="item.icon" :size="19" /><span>{{ item.label }}</span></button>
        </nav>
        <div class="nav-group-title admin-title">管理后台 <span>WORKSPACE</span></div>
        <nav class="side-nav" aria-label="管理后台">
          <button v-for="item in adminNav" :key="item.id" type="button" :class="{ active: page === item.id }" @click="setPage(item.id)"><Icon :name="item.icon" :size="19" /><span>{{ item.label }}</span></button>
        </nav>
      </div>
      <button class="sidebar-profile" type="button" @click="setPage('membership')">
        <span class="profile-avatar">林</span><span class="profile-copy"><strong>林间听雨</strong><small>年度会员 · 有效期内</small></span><Icon name="chevron" :size="16" />
      </button>
    </aside>

    <div class="app-main">
      <header class="topbar">
        <div class="topbar-left"><button class="mobile-menu icon-button" type="button" aria-label="打开菜单" @click="mobileOpen = true"><Icon name="menu" /></button><span class="breadcrumb">声屿音乐</span><span class="breadcrumb-slash">/</span><strong>{{ titles[page] }}</strong></div>
        <div class="topbar-right"><span class="prototype-pill"><span></span> 原型预览</span><button class="top-search" type="button" @click="setPage('search')"><Icon name="search" :size="17" /><span>搜索歌曲、歌手或专辑</span><kbd>⌘ K</kbd></button><button class="icon-button notification" type="button" aria-label="通知" @click="notice()"><Icon name="bell" :size="20" /><i></i></button><button class="top-avatar" type="button" @click="setPage('membership')">林</button></div>
      </header>

      <main class="content" :class="[`page-${page}`]">
        <template v-if="page === 'home'">
          <section class="hero-card">
            <div class="hero-copy"><span class="eyebrow light"><span class="eyebrow-line"></span> YOUR DAILY SOUNDTRACK</span><h1>让音乐<br /><em>找到你。</em></h1><p>在每一个值得被记住的瞬间，遇见一首刚好懂你的歌。</p><button class="button button-light" type="button" @click="setPage('recommend')">探索今日推荐 <Icon name="arrow" :size="18" /></button><div class="hero-counter"><strong>02 / 05</strong><span></span><small>每日精选</small></div></div>
            <div class="hero-art"><div class="hero-vinyl"><span></span></div><div class="hero-art-cover"><AlbumCover :track="tracks[0]" size="hero" show-title /></div><span class="hero-sticker">FOR YOUR<br />EARS ONLY ✳</span></div>
          </section>
          <div class="section-heading"><div><span class="eyebrow">MADE FOR YOU</span><h2>今天，听点不一样的</h2></div><button class="text-link" type="button" @click="setPage('recommend')">查看全部 <Icon name="arrow" :size="17" /></button></div>
          <div class="feature-grid"><button class="feature-card feature-daily" type="button" @click="setPage('recommend')"><span class="feature-icon"><Icon name="spark" :size="22" /></span><span class="feature-graphic"><i></i><i></i><i></i></span><span class="feature-copy"><small>每日更新 · 为你挑选</small><strong>每日推荐</strong><span>从你喜欢的旋律出发，发现更多可能。</span></span><Icon name="arrow" :size="20" class="feature-arrow" /></button><button class="feature-card feature-ai" type="button" @click="setPage('ai')"><span class="feature-icon"><Icon name="spark" :size="22" /></span><span class="feature-ai-quote">“想听适合雨天独处的<br />温柔中文歌”</span><span class="feature-copy"><small>描述心情 · 发现好歌</small><strong>AI 灵感查歌</strong><span>说出你的心情，让音乐接住你。</span></span><Icon name="arrow" :size="20" class="feature-arrow" /></button></div>
          <div class="section-heading compact"><div><span class="eyebrow">TRENDING NOW</span><h2>正在被大家喜欢</h2></div><button class="text-link" type="button" @click="setPage('search')">探索曲库 <Icon name="arrow" :size="17" /></button></div>
          <TrackTable :tracks="tracks.slice(0, 5)" show-plays @select="selectTrack" />
          <div class="home-bottom"><div class="small-quote">“有些时刻，<br />需要一首歌来命名。”<span>— 声屿音乐</span></div><div class="mini-membership"><span class="eyebrow">SHENGYU PLUS</span><h3>好音乐，尽情听。</h3><p>解锁会员歌曲完整播放，留住每一个心动瞬间。</p><button type="button" @click="setPage('membership')">了解会员权益 <Icon name="arrow" :size="17" /></button></div></div>
        </template>

        <template v-else-if="page === 'search'">
          <div class="page-intro"><span class="eyebrow">EXPLORE THE SOUND</span><h1>找到下一首<span class="accent-dot">.</span></h1><p>从熟悉的名字，到从未听过的惊喜。</p></div>
          <div class="search-panel"><Icon name="search" :size="24" /><input v-model="query" type="search" placeholder="搜索歌曲、歌手、专辑或风格" aria-label="搜索歌曲" /><button type="button" @click="notice('筛选面板将在业务接入后开放')"><Icon name="filter" :size="18" /> 筛选</button></div>
          <div class="chip-row"><button v-for="item in categories" :key="item" type="button" :class="{ selected: category === item }" @click="category = item">{{ item }}</button></div>
          <div class="section-heading"><div><span class="eyebrow">ALL TRACKS</span><h2>曲库歌曲 <sup>{{ filteredTracks.length.toString().padStart(2, '0') }}</sup></h2></div><span class="subtle-text">按热度排序 <span class="sort-arrow">⌄</span></span></div>
          <TrackTable :tracks="filteredTracks" @select="selectTrack" /><div v-if="filteredTracks.length === 0" class="empty-state">没有找到相符的模拟歌曲，试试别的关键词。</div>
        </template>

        <template v-else-if="page === 'player'">
          <div class="player-stage"><div class="player-artwork"><div class="player-back-disc"></div><AlbumCover :track="currentTrack" size="player" show-title /></div><div class="player-content"><span class="eyebrow"><span class="live-dot"></span> NOW PLAYING</span><div class="player-meta"><span class="music-type">{{ currentTrack.category }} · {{ currentTrack.album }}</span><h1>{{ currentTrack.title }}</h1><p>{{ currentTrack.artist }}</p></div><div class="player-tags"><span v-if="currentTrack.vip"><Icon name="crown" :size="14" /> 会员歌曲</span><span>高品质音质</span><span>单曲循环</span></div><div class="player-progress"><div class="progress-line"><span></span></div><div><span>01:24</span><span>{{ currentTrack.duration }}</span></div></div><div class="player-controls"><button type="button" aria-label="随机播放" @click="notice()"><Icon name="shuffle" :size="20" /></button><button type="button" aria-label="上一首" @click="selectTrack(tracks[(tracks.findIndex(t => t.id === currentTrack.id) + tracks.length - 1) % tracks.length])"><Icon name="prev" :size="25" /></button><button type="button" class="play-main" :aria-label="playing ? '暂停' : '播放'" @click="playing = !playing"><Icon :name="playing ? 'pause' : 'play'" :size="25" /></button><button type="button" aria-label="下一首" @click="selectTrack(tracks[(tracks.findIndex(t => t.id === currentTrack.id) + 1) % tracks.length])"><Icon name="next" :size="25" /></button><button type="button" aria-label="循环播放" @click="notice()"><Icon name="repeat" :size="20" /></button></div><div class="player-actions"><button type="button" @click="notice()"><Icon name="heart" :size="18" /> 收藏歌曲</button><button type="button" @click="notice()"><Icon name="list" :size="18" /> 加入歌单</button><button type="button" @click="notice()"><Icon name="volume" :size="19" /></button></div><p class="player-note">播放控件仅用于视觉预览，当前没有音频文件。</p></div></div>
          <div class="section-heading compact"><div><span class="eyebrow">KEEP LISTENING</span><h2>接下来听什么</h2></div></div><TrackTable :tracks="tracks.filter(t => t.id !== currentTrack.id).slice(0, 4)" @select="selectTrack" />
        </template>

        <template v-else-if="page === 'recommend'">
          <section class="recommend-hero"><div><span class="eyebrow light">CURATED JUST FOR YOU · 2026.10.23</span><h1>每日推荐<span> / 01</span></h1><p>从你的收听足迹里，打捞今天的好心情。<br />每天都有新的声音，刚好适合现在的你。</p><button class="button button-light" type="button" @click="selectTrack(tracks[0])"><Icon name="play" :size="17" /> 播放第一首</button></div><div class="recommend-visual"><div class="recommend-circle c1"></div><div class="recommend-circle c2"></div><div class="recommend-circle c3"></div><span>YOU<br />+<br />MUSIC</span></div></section>
          <div class="rec-insight"><div><Icon name="spark" :size="20" /><span>根据你最近偏爱的 <strong>独立流行</strong> 和 <strong>温柔民谣</strong> 生成</span></div><span>每日 06:00 更新</span></div>
          <div class="section-heading"><div><span class="eyebrow">THE LIST</span><h2>今日为你精选</h2></div><span class="subtle-text">共 8 首 · 约 33 分钟</span></div><TrackTable :tracks="tracks" @select="selectTrack" />
          <div class="algorithm-note"><div class="algorithm-mark">✳</div><div><strong>推荐，应该更懂你。</strong><p>标签规则与协同过滤共同寻找适合你的歌曲。未来可在这里查看推荐理由与生成时间。</p></div><button type="button" @click="notice()">了解推荐逻辑 <Icon name="arrow" :size="16" /></button></div>
        </template>

        <template v-else-if="page === 'ai'">
          <div class="ai-layout"><section class="ai-main"><div class="ai-heading"><span class="ai-avatar"><Icon name="spark" :size="25" /></span><div><span class="eyebrow">YOUR MUSIC COMPANION</span><h1>把心情，说给音乐听<span class="accent-dot">.</span></h1><p>不记得歌名也没关系，描述一个场景、一种感觉，或一句模糊的记忆。</p></div></div><div class="chat-day">今天 · 10 月 23 日</div><div class="chat-message user-message">想听一些适合雨天独处的温柔中文歌</div><div class="chat-message ai-message"><span class="ai-message-icon"><Icon name="spark" :size="16" /></span><div><strong>我找到了几首适合你的歌。</strong><p>柔和的旋律、安静的叙述，陪你把雨声听成背景。</p><div class="ai-results"><button v-for="track in tracks.slice(0, 3)" :key="track.id" type="button" @click="selectTrack(track)"><AlbumCover :track="track" size="tiny" /><span><strong>{{ track.title }}</strong><small>{{ track.artist }} · {{ track.category }}</small></span><Icon name="play" :size="17" /></button></div></div></div><div class="chat-message user-message second">再轻快一点，适合傍晚散步</div><div class="chat-message ai-message"><span class="ai-message-icon"><Icon name="spark" :size="16" /></span><div><strong>换一份更轻盈的歌单给你。</strong><p>保留温柔感，加一点夕阳和沿途的风。</p><div class="ai-results"><button v-for="track in [tracks[3], tracks[5]]" :key="track.id" type="button" @click="selectTrack(track)"><AlbumCover :track="track" size="tiny" /><span><strong>{{ track.title }}</strong><small>{{ track.artist }} · {{ track.category }}</small></span><Icon name="play" :size="17" /></button></div></div></div><div class="chat-input"><input type="text" placeholder="继续描述你想听的音乐…" @keydown.enter="notice()" /><button type="button" aria-label="发送" @click="notice()"><Icon name="send" :size="18" /></button></div><p class="ai-disclaimer">只推荐本地曲库中的歌曲 · 示例对话仅供原型审查</p></section><aside class="ai-side"><div class="ai-side-card"><span class="eyebrow">START WITH A FEELING</span><h3>不知道从哪说起？</h3><button v-for="sample in ['适合周末清晨的慵懒音乐', '想听有海边感觉的中文歌', '帮我找一首深夜散步的歌']" :key="sample" type="button" @click="notice('示例提问将在 AI 接口接入后启用')">{{ sample }} <Icon name="arrow" :size="16" /></button></div><div class="ai-tip"><span>✳</span><p>你可以继续追问：<br /><strong>“更轻快一点”</strong> 或 <strong>“只看女声”</strong></p></div></aside></div>
        </template>

        <template v-else-if="page === 'favorites'">
          <section class="collection-hero"><div class="collection-icon"><Icon name="heart" :size="72" /></div><div><span class="eyebrow light">YOUR COLLECTION</span><h1>喜欢的音乐，<br />永远有位置。</h1><p>8 首收藏歌曲 · 最近更新于今天</p><button class="button button-light" type="button" @click="selectTrack(tracks[0])"><Icon name="play" :size="17" /> 播放收藏</button></div></section><div class="section-heading"><div><span class="eyebrow">SAVED TRACKS</span><h2>我的收藏</h2></div><span class="subtle-text">按最近收藏排序</span></div><TrackTable :tracks="tracks" @select="selectTrack" />
        </template>

        <template v-else-if="page === 'playlists'">
          <div class="page-intro with-action"><div><span class="eyebrow">MADE BY YOU</span><h1>我的歌单<span class="accent-dot">.</span></h1><p>把喜欢的声音，收进自己的故事里。</p></div><button class="button button-accent" type="button" @click="notice()"><Icon name="plus" :size="18" /> 新建歌单</button></div><div class="playlist-grid"><button v-for="(playlist, index) in playlists" :key="playlist.title" class="playlist-card" type="button" @click="notice('歌单详情将在业务接入后开放')"><div class="playlist-cover" :class="playlist.tone"><span class="playlist-symbol">{{ playlist.icon }}</span><span class="playlist-no">0{{ index + 1 }} / 04</span></div><strong>{{ playlist.title }}</strong><small>{{ playlist.subtitle }}</small></button><button class="playlist-create" type="button" @click="notice()"><span><Icon name="plus" :size="30" /></span><strong>创建新的歌单</strong><small>给下一段心情一个名字</small></button></div>
        </template>

        <template v-else-if="page === 'history'">
          <div class="page-intro with-action"><div><span class="eyebrow">YOUR LISTENING JOURNEY</span><h1>那些听过的歌<span class="accent-dot">.</span></h1><p>每一首，都曾陪你度过一个时刻。</p></div><span class="history-count"><Icon name="headphones" :size="20" /> 本周收听 42 次</span></div><div class="history-overview"><div><strong>42</strong><span>本周播放</span></div><div><strong>186<small>分钟</small></strong><span>聆听时长</span></div><div><strong>12</strong><span>发现新歌</span></div><div class="history-mood"><span>本周最常听的风格</span><strong>独立流行 <span>↗</span></strong></div></div><div class="section-heading"><div><span class="eyebrow">RECENTLY PLAYED</span><h2>最近播放</h2></div><span class="subtle-text">最近 30 天</span></div><TrackTable :tracks="[...tracks.slice(0, 4), ...tracks.slice(4, 8)]" show-date @select="selectTrack" />
        </template>

        <template v-else-if="page === 'membership'">
          <section class="membership-hero"><div><span class="eyebrow light"><Icon name="crown" :size="17" /> SHENGYU PLUS</span><h1>让每一首喜欢的歌，<br /><em>都能完整抵达。</em></h1><p>解锁会员歌曲完整播放，音乐不必停在前奏。</p><div class="membership-status"><span class="status-signal"></span> 年度会员生效中 <span class="status-divider"></span> 有效期至 2027.10.22</div></div><div class="member-mark"><span>声屿</span><strong>PLUS</strong><small>MEMBER SINCE 2026</small></div></section><div class="section-heading"><div><span class="eyebrow">YOUR BENEFITS</span><h2>属于你的聆听特权</h2></div></div><div class="benefit-grid"><div><span><Icon name="disc" :size="28" /></span><strong>完整播放</strong><p>会员歌曲，从第一秒听到最后一秒。</p></div><div><span><Icon name="music" :size="28" /></span><strong>更多好歌</strong><p>探索专属于会员的精选音乐内容。</p></div><div><span><Icon name="headphones" :size="28" /></span><strong>沉浸体验</strong><p>让喜欢的旋律，陪伴更久一点。</p></div></div><div class="member-info"><div><span class="eyebrow">MEMBERSHIP DETAILS</span><h3>会员信息</h3><p>当前身份 <strong>年度会员</strong></p><p>生效时间 <strong>2026 年 10 月 23 日</strong></p><p>到期时间 <strong>2027 年 10 月 22 日</strong></p></div><div class="member-notice"><Icon name="check" :size="22" /><span>会员资格由管理员开通与管理。<br />如需调整，请联系管理员。</span></div></div>
        </template>

        <template v-else-if="page === 'dashboard'">
          <div class="page-intro admin-intro"><span class="eyebrow">OVERVIEW / 2026.10.23</span><h1>数据概览<span class="accent-dot">.</span></h1><p>从每一次播放、收藏与分享，看见音乐的回响。</p></div><div class="metric-grid"><div class="metric-card"><span>累计用户 <Icon name="users" :size="19" /></span><strong>2,846</strong><small><b>↑ 12.8%</b> 较上月</small></div><div class="metric-card"><span>有效会员 <Icon name="crown" :size="19" /></span><strong>638</strong><small><b>↑ 8.2%</b> 较上月</small></div><div class="metric-card"><span>上架歌曲 <Icon name="music" :size="19" /></span><strong>1,284</strong><small><b>↑ 24</b> 本月新增</small></div><div class="metric-card"><span>今日播放 <Icon name="headphones" :size="19" /></span><strong>18,392</strong><small><b>↑ 16.4%</b> 较昨日</small></div></div><div class="dashboard-grid"><div class="dashboard-panel chart-panel"><div class="panel-heading"><div><span class="eyebrow">LISTENING ACTIVITY</span><h3>播放趋势</h3></div><span class="panel-select">近 7 天 ⌄</span></div><div class="line-chart"><div class="chart-gridline l1"></div><div class="chart-gridline l2"></div><div class="chart-gridline l3"></div><svg viewBox="0 0 650 190" preserveAspectRatio="none" aria-label="模拟的七日播放趋势"><defs><linearGradient id="chartfill" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#f68e6c" stop-opacity=".25"/><stop offset="1" stop-color="#f68e6c" stop-opacity="0"/></linearGradient></defs><path d="M0 144 C48 132 70 125 110 129 S170 145 215 104 S285 121 330 88 S400 112 445 65 S520 78 555 54 S610 69 650 24 V190 H0Z" fill="url(#chartfill)"/><path d="M0 144 C48 132 70 125 110 129 S170 145 215 104 S285 121 330 88 S400 112 445 65 S520 78 555 54 S610 69 650 24" fill="none" stroke="#fa9575" stroke-width="3"/><circle cx="650" cy="24" r="6" fill="#fa9575" stroke="#251e1d" stroke-width="3"/></svg></div><div class="chart-labels"><span>10/17</span><span>10/18</span><span>10/19</span><span>10/20</span><span>10/21</span><span>10/22</span><span>10/23</span></div></div><div class="dashboard-panel genres-panel"><div class="panel-heading"><div><span class="eyebrow">TOP GENRES</span><h3>热门风格</h3></div></div><div class="donut-chart"><span><strong>42%</strong><small>流行</small></span></div><div class="genre-legend"><span><i class="g-pop"></i>流行 <b>42%</b></span><span><i class="g-folk"></i>民谣 <b>28%</b></span><span><i class="g-rock"></i>独立摇滚 <b>18%</b></span><span><i class="g-other"></i>其他 <b>12%</b></span></div></div></div><div class="dashboard-grid bottom-grid"><div class="dashboard-panel"><div class="panel-heading"><div><span class="eyebrow">TOP TRACKS</span><h3>热门歌曲</h3></div><button class="text-link" type="button" @click="setPage('songs')">查看全部 <Icon name="arrow" :size="16" /></button></div><div class="mini-track" v-for="(track, index) in tracks.slice(0, 4)" :key="track.id"><span>{{ index + 1 }}</span><AlbumCover :track="track" size="tiny" /><div><strong>{{ track.title }}</strong><small>{{ track.artist }}</small></div><b>{{ track.plays }}</b></div></div><div class="dashboard-panel experiment-panel"><div class="panel-heading"><div><span class="eyebrow">RECOMMENDATION EXPERIMENT</span><h3>算法对比</h3></div><span class="exp-tag">示意数据</span></div><div class="exp-row"><span>标签规则推荐</span><div><i style="width: 62%"></i></div><strong>0.124</strong></div><div class="exp-row"><span>协同过滤推荐</span><div><i style="width: 75%"></i></div><strong>0.150</strong></div><p>Precision@10 · 本区数值为布局示意，正式实验后替换。</p></div></div>
        </template>

        <template v-else-if="page === 'members'">
          <div class="page-intro with-action admin-intro"><div><span class="eyebrow">PEOPLE & MEMBERSHIP</span><h1>用户与会员<span class="accent-dot">.</span></h1><p>了解每一位听众，管理会员权益与有效期。</p></div><button class="button button-accent" type="button" @click="notice()"><Icon name="plus" :size="18" /> 开通会员</button></div><div class="admin-stat-strip"><div><strong>2,846</strong><span>总用户数</span></div><div><strong>638</strong><span>有效会员</span></div><div><strong>22.4%</strong><span>会员占比</span></div><div><strong>47</strong><span>即将到期</span></div></div><div class="admin-list-panel"><div class="panel-heading list-heading"><div><h3>用户列表 <sup>2,846</sup></h3><span>管理用户与会员状态</span></div><button class="outline-button" type="button" @click="notice()"><Icon name="filter" :size="17" /> 筛选用户</button></div><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>用户</th><th>用户 ID</th><th>会员类型</th><th>到期时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="member in members" :key="member.id"><td><span class="member-cell"><span class="list-avatar" :class="member.color">{{ member.initials }}</span><strong>{{ member.name }}</strong></span></td><td class="muted">{{ member.id }}</td><td>{{ member.type }}</td><td class="muted">{{ member.expiry }}</td><td><span class="status-pill" :class="member.status === '生效中' ? 'success' : member.status === '已过期' ? 'expired' : 'neutral'">{{ member.status }}</span></td><td><button class="table-action" type="button" @click="notice()">管理 <Icon name="chevron" :size="14" /></button></td></tr></tbody></table></div><div class="pagination"><span>显示 1–5 条，共 2,846 条</span><div><button type="button" @click="notice()">‹</button><button class="current" type="button">1</button><button type="button" @click="notice()">2</button><button type="button" @click="notice()">3</button><button type="button" @click="notice()">›</button></div></div></div>
        </template>

        <template v-else-if="page === 'songs'">
          <div class="page-intro with-action admin-intro"><div><span class="eyebrow">MUSIC CATALOG</span><h1>歌曲管理<span class="accent-dot">.</span></h1><p>让好音乐被发现，也让每一首歌都有归处。</p></div><button class="button button-accent" type="button" @click="notice()"><Icon name="upload" :size="18" /> 上传歌曲</button></div><div class="admin-stat-strip"><div><strong>1,284</strong><span>歌曲总数</span></div><div><strong>1,216</strong><span>已上架</span></div><div><strong>68</strong><span>已下架</span></div><div><strong>326</strong><span>会员歌曲</span></div></div><div class="admin-list-panel"><div class="panel-heading list-heading"><div><h3>全部歌曲 <sup>1,284</sup></h3><span>编辑曲目信息、标签与上下架状态</span></div><button class="outline-button" type="button" @click="notice()"><Icon name="filter" :size="17" /> 筛选歌曲</button></div><div class="admin-table-wrap"><table class="admin-table song-admin-table"><thead><tr><th>歌曲</th><th>专辑 / 风格</th><th>会员权益</th><th>播放量</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="(track, index) in tracks.slice(0, 6)" :key="track.id"><td><span class="song-cell"><AlbumCover :track="track" size="tiny" /><span><strong>{{ track.title }}</strong><small>{{ track.artist }}</small></span></span></td><td><span>{{ track.album }}</span><small class="table-sub">{{ track.category }}</small></td><td><span v-if="track.vip" class="vip-label">VIP 完整播放</span><span v-else class="muted">免费歌曲</span></td><td>{{ track.plays }}</td><td><span class="status-pill" :class="index === 5 ? 'neutral' : 'success'">{{ index === 5 ? '已下架' : '已上架' }}</span></td><td><button class="table-action" type="button" @click="notice()">编辑 <Icon name="chevron" :size="14" /></button></td></tr></tbody></table></div><div class="pagination"><span>显示 1–6 条，共 1,284 条</span><div><button type="button" @click="notice()">‹</button><button class="current" type="button">1</button><button type="button" @click="notice()">2</button><button type="button" @click="notice()">3</button><button type="button" @click="notice()">›</button></div></div></div>
        </template>

        <template v-else-if="page === 'comments'">
          <div class="page-intro admin-intro"><span class="eyebrow">COMMUNITY VOICES</span><h1>评论管理<span class="accent-dot">.</span></h1><p>让真诚的音乐分享，被更多人听见。</p></div><div class="admin-stat-strip"><div><strong>3,592</strong><span>评论总数</span></div><div><strong>24</strong><span>待审核</span></div><div><strong>3,541</strong><span>已通过</span></div><div><strong>27</strong><span>已隐藏</span></div></div><div class="comment-tabs"><button class="selected" type="button">全部评论 <span>3,592</span></button><button type="button" @click="notice()">待审核 <span>24</span></button><button type="button" @click="notice()">已通过</button><button type="button" @click="notice()">已隐藏</button></div><div class="comment-list"><div v-for="comment in comments" :key="comment.user" class="comment-card"><span class="list-avatar" :class="comment.color">{{ comment.initials }}</span><div class="comment-body"><div class="comment-meta"><strong>{{ comment.user }}</strong><span>评论了 <b>《{{ comment.song }}》</b></span><span>· {{ comment.time }}</span></div><p>{{ comment.text }}</p><span class="status-pill" :class="comment.status === '待审核' ? 'pending' : 'success'">{{ comment.status }}</span></div><div class="comment-actions"><button type="button" @click="notice()">{{ comment.status === '待审核' ? '通过' : '隐藏' }}</button><button type="button" @click="notice()"><Icon name="more" :size="20" /></button></div></div></div>
        </template>
      </main>

      <footer class="footer-player"><div class="footer-track"><AlbumCover :track="currentTrack" size="tiny" /><div><strong>{{ currentTrack.title }}</strong><small>{{ currentTrack.artist }}</small></div><button type="button" aria-label="收藏" @click="notice()"><Icon name="heart" :size="17" /></button></div><div class="footer-center"><div class="footer-controls"><button type="button" aria-label="上一首" @click="selectTrack(tracks[(tracks.findIndex(t => t.id === currentTrack.id) + tracks.length - 1) % tracks.length])"><Icon name="prev" :size="19" /></button><button class="footer-play" type="button" :aria-label="playing ? '暂停' : '播放'" @click="playing = !playing"><Icon :name="playing ? 'pause' : 'play'" :size="17" /></button><button type="button" aria-label="下一首" @click="selectTrack(tracks[(tracks.findIndex(t => t.id === currentTrack.id) + 1) % tracks.length])"><Icon name="next" :size="19" /></button></div><div class="footer-progress"><span>01:24</span><div><i></i></div><span>{{ currentTrack.duration }}</span></div></div><div class="footer-tools"><Icon name="volume" :size="19" /><div><i></i></div><button type="button" aria-label="打开播放页" @click="setPage('player')"><Icon name="list" :size="19" /></button></div></footer>
    </div>
    <Transition name="toast"><div v-if="toast" class="toast-message">{{ toast }}</div></Transition>
  </div>
</template>
