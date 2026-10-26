export const tracks = [
  { id: 1, title: '晚风来信', artist: '许南枝', album: '夜航日记', duration: '03:42', category: '独立流行', tone: 'cover-sunset', vip: false, plays: '12.8万', date: '2026-10-21' },
  { id: 2, title: '把海留给你', artist: '岛屿电台', album: '海岸来信', duration: '04:16', category: '民谣', tone: 'cover-sea', vip: true, plays: '9.6万', date: '2026-10-20' },
  { id: 3, title: '凌晨三点的城市', artist: '光点乐队', album: '未眠时刻', duration: '03:58', category: '流行摇滚', tone: 'cover-night', vip: false, plays: '8.2万', date: '2026-10-19' },
  { id: 4, title: '日落之前', artist: '南方邮差', album: '夏日尽头', duration: '04:05', category: '独立摇滚', tone: 'cover-orange', vip: false, plays: '7.4万', date: '2026-10-18' },
  { id: 5, title: '山野慢行', artist: '微光合唱团', album: '山间来客', duration: '03:36', category: '民谣', tone: 'cover-green', vip: true, plays: '6.1万', date: '2026-10-17' },
  { id: 6, title: '云上散步', artist: '雾色星期天', album: '云端漫游', duration: '04:32', category: '梦幻流行', tone: 'cover-cloud', vip: false, plays: '5.8万', date: '2026-10-16' },
  { id: 7, title: '留一盏灯', artist: '林与晚', album: '温柔宇宙', duration: '04:12', category: '流行', tone: 'cover-light', vip: true, plays: '5.2万', date: '2026-10-15' },
  { id: 8, title: '漫游星群', artist: '远空计划', album: '星河旅人', duration: '05:09', category: '独立摇滚', tone: 'cover-space', vip: false, plays: '4.9万', date: '2026-10-14' }
]

export const playlists = [
  { title: '深夜留声机', subtitle: '18 首歌曲 · 最近更新于今天', tone: 'cover-night', icon: '☾' },
  { title: '沿着海岸线', subtitle: '24 首歌曲 · 最近更新于昨天', tone: 'cover-sea', icon: '〰' },
  { title: '周末醒来之后', subtitle: '12 首歌曲 · 最近更新于 10 月 18 日', tone: 'cover-orange', icon: '☀' },
  { title: '适合一个人的散步', subtitle: '31 首歌曲 · 最近更新于 10 月 12 日', tone: 'cover-green', icon: '✳' }
]

export const comments = [
  { user: '木木的耳朵', initials: '木', color: 'avatar-peach', song: '晚风来信', text: '前奏一响，感觉傍晚的风已经吹过来了。', time: '2 分钟前', status: '待审核' },
  { user: '一杯热拿铁', initials: '热', color: 'avatar-blue', song: '把海留给你', text: '想在海边循环一整个下午。', time: '18 分钟前', status: '已通过' },
  { user: '山间拾光', initials: '山', color: 'avatar-green', song: '山野慢行', text: '很适合通勤路上听，让人慢慢安静下来。', time: '1 小时前', status: '已通过' },
  { user: '小岛来信', initials: '岛', color: 'avatar-yellow', song: '凌晨三点的城市', text: '夜里听这首歌，真的很有画面感。', time: '3 小时前', status: '待审核' }
]

export const members = [
  { name: '林间听雨', id: 'U2026102301', initials: '林', color: 'avatar-peach', type: '年度会员', expiry: '2027-10-22', status: '生效中' },
  { name: '一杯热拿铁', id: 'U2026102108', initials: '热', color: 'avatar-blue', type: '月度会员', expiry: '2026-11-21', status: '生效中' },
  { name: '山间拾光', id: 'U2026101503', initials: '山', color: 'avatar-green', type: '普通用户', expiry: '—', status: '未开通' },
  { name: '小岛来信', id: 'U2026100906', initials: '岛', color: 'avatar-yellow', type: '月度会员', expiry: '2026-10-20', status: '已过期' },
  { name: '云朵收藏家', id: 'U2026092804', initials: '云', color: 'avatar-purple', type: '普通用户', expiry: '—', status: '未开通' }
]
