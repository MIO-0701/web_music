import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const manifestPath = path.join(root, 'img', '_cover-manifest.json')
const outputPath = path.join(root, 'data', 'song-genres.json')
const maxSongs = Number(process.argv.find(arg => arg.startsWith('--limit='))?.slice(8) || 0)
const retryReview = process.argv.includes('--retry-review')
const zhVariants = JSON.parse(fs.readFileSync(path.join(root, 'data', 'zh-variants.json'), 'utf8'))
const delayMs = 3200 // Apple's public Search API documents approximately 20 calls/minute.
const store = { cn: 'HK', en: 'US', fr: 'FR', jp: 'JP', ru: 'US' }
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
let lastRequestAt = 0

function normal(text, chinese = false) {
  const value = chinese ? [...String(text || '')].map(char => zhVariants[char] || char).join('') : String(text || '')
  return value.normalize('NFKC').toLowerCase()
    .replace(/\b(feat\.?|featuring|with)\b/gu, '')
    .replace(/[\s\p{P}\p{S}]/gu, '')
}

function similarity(left, right, chinese = false) {
  const a = [...normal(left, chinese)], b = [...normal(right, chinese)]
  if (!a.length || !b.length) return 0
  const row = Array.from({ length: b.length + 1 }, (_, i) => i)
  for (let i = 1; i <= a.length; i++) {
    let diagonal = row[0]
    row[0] = i
    for (let j = 1; j <= b.length; j++) {
      const previous = row[j]
      row[j] = Math.min(row[j] + 1, row[j - 1] + 1, diagonal + (a[i - 1] === b[j - 1] ? 0 : 1))
      diagonal = previous
    }
  }
  return 1 - row[b.length] / Math.max(a.length, b.length)
}

function artistSimilarity(expected, actual, chinese = false) {
  const expectedName = normal(expected, chinese)
  const actualName = normal(actual, chinese)
  if (expectedName.length >= 2 && actualName.includes(expectedName)) return 1
  const whole = similarity(expected, actual, chinese)
  const parts = String(expected).split(/[&、，,\/]/u).map(x => x.trim()).filter(Boolean)
  return Math.max(whole, ...parts.map(part => similarity(part, actual, chinese)))
}

function versionsCompatible(expected, actual) {
  const patterns = [/\blive\b|ライブ|现场|現場|演唱会|演唱會/iu, /\bdemo\b|デモ/iu,
    /\bremix\b|リミックス|混音/iu, /\bacoustic\b|アコースティック|不插电|不插電/iu,
    /\binstrumental\b|インストゥルメンタル|纯音乐|純音樂/iu,
    /\bkaraoke\b|カラオケ|伴奏/iu, /\bcover\b|カバー/iu]
  return patterns.every(pattern => pattern.test(expected) === pattern.test(actual))
}

function classification(raw) {
  const value = String(raw || '').toLowerCase()
  const rules = [
    [/j-pop|japanese pop|mandopop|cantopop|國語流行|国语流行|廣東歌|香港流行|流行樂|流行音乐|\bpop\b|ポップ/u, ['pop', '流行', /j-pop|japanese pop/u.test(value) ? 'J-Pop' : /國語流行|国语流行|mandopop/u.test(value) ? '华语流行' : /廣東歌|香港流行|cantopop/u.test(value) ? '粤语流行' : null]],
    [/j-rock|\brock\b|ロック|摇滚|搖滾/u, ['rock', '摇滚', /j-rock/u.test(value) ? 'J-Rock' : null]],
    [/alternative|オルタナティブ|另类|另類/u, ['alternative', '另类', null]],
    [/r&b|rhythm and blues|soul|ソウル|节奏布鲁斯|節奏藍調/u, ['rnb-soul', 'R&B / 灵魂', null]],
    [/hip.hop|\brap\b|ヒップホップ|说唱|說唱/u, ['hip-hop', '嘻哈 / 说唱', null]],
    [/electronic|エレクトロニック|電子/u, ['electronic', '电子', null]],
    [/\bdance\b|ダンス|舞曲/u, ['dance', '舞曲', null]],
    [/\bjazz\b|ジャズ|爵士/u, ['jazz', '爵士', null]],
    [/classical|クラシック|古典/u, ['classical', '古典', null]],
    [/folk|フォーク|民谣|民謠/u, ['folk', '民谣', null]],
    [/singer.songwriter|シンガーソングライター|唱作/u, ['singer-songwriter', '唱作', null]],
    [/soundtrack|サウンドトラック|电影配乐|影視原聲|原聲帶|原声/u, ['soundtrack', '原声 / 配乐', null]],
    [/anime|アニメ|动画|動畫/u, ['anime', '动漫音乐', null]],
    [/country|カントリー|乡村|鄉村/u, ['country', '乡村', null]],
    [/metal|メタル|金属|金屬/u, ['metal', '金属', null]],
    [/blues|ブルース|蓝调|藍調/u, ['blues', '蓝调', null]],
    [/reggae|レゲエ|雷鬼/u, ['reggae', '雷鬼', null]],
    [/latin|ラテン|拉丁/u, ['latin', '拉丁', null]],
    [/instrumental|インストゥルメンタル|纯音乐|純音樂/u, ['instrumental', '器乐', null]],
    [/new age|ニューエイジ|新世纪|新世紀/u, ['new-age', '新世纪', null]],
  ]
  const match = rules.find(([pattern]) => pattern.test(value))
  if (!match) return { genre: null, genreName: null, styles: [] }
  const [, [genre, genreName, style]] = match
  return { genre, genreName, styles: style ? [style] : [] }
}

async function search(term, country) {
  await sleep(Math.max(0, delayMs - (Date.now() - lastRequestAt)))
  lastRequestAt = Date.now()
  const url = new URL('https://itunes.apple.com/search')
  url.search = new URLSearchParams({ term, country, media: 'music', entity: 'song', limit: '100' }).toString()
  for (let attempt = 0; attempt < 2; attempt++) {
    const response = await fetch(url, { signal: AbortSignal.timeout(20000), headers: { 'User-Agent': 'ShengyuMusicGenreResearch/1.0 (metadata only)' } })
    if (response.status === 429 || response.status >= 500) {
      if (attempt === 0) { await sleep(10000); lastRequestAt = Date.now(); continue }
    }
    if (!response.ok) throw new Error(`Apple Search API HTTP ${response.status}`)
    return { url: url.toString(), results: (await response.json()).results || [] }
  }
  throw new Error('Apple Search API unavailable')
}

function bestMatch(entry, results) {
  const ranked = results.filter(item => item.kind === 'song' && item.primaryGenreName && versionsCompatible(entry.title, item.trackName)).map(item => {
    const title = similarity(entry.title, item.trackName, entry.category === 'cn')
    const artist = artistSimilarity(entry.artist, item.artistName, entry.category === 'cn')
    return { item, title, artist, score: title * 0.68 + artist * 0.32 }
  }).filter(hit => hit.title >= 0.66 && hit.artist >= 0.55 && hit.score >= 0.75)
    .sort((a, b) => b.score - a.score || b.title - a.title || b.artist - a.artist)
  return ranked[0] || null
}

function save(rows) {
  const counts = rows.reduce((acc, row) => { acc[row.status] = (acc[row.status] || 0) + 1; return acc }, {})
  const output = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    sourceManifest: 'img/_cover-manifest.json',
    sourceDocumentation: 'https://performance-partners.apple.com/search-api',
    description: '曲风来源为 Apple 曲目 primaryGenreName；无可靠匹配的歌曲保留待复核，不根据语言推断风格。此文件不自动修改数据库。',
    counts,
    songs: rows,
  }
  const temp = outputPath + '.tmp'
  fs.writeFileSync(temp, JSON.stringify(output, null, 2) + '\n', 'utf8')
  fs.renameSync(temp, outputPath)
}

const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
  .filter(entry => entry.status === 'matched' && fs.existsSync(path.join(root, 'music', entry.category, entry.file)))
const previous = fs.existsSync(outputPath) ? JSON.parse(fs.readFileSync(outputPath, 'utf8')) : null
const old = new Map((previous?.songs || []).map(row => [row.sourcePath, row]))
const rows = manifest.map(entry => {
  const sourcePath = `${entry.category}/${entry.file}`
  return old.get(sourcePath) || {
    sourcePath, title: entry.title, artist: entry.artist, language: entry.category,
    genre: null, genreName: null, styles: [], status: 'pending', confidence: null, evidence: null,
  }
})
if (retryReview) {
  for (const row of rows) {
    if (row.status === 'matched' && row.evidence?.matchedTitle && !versionsCompatible(row.title, row.evidence.matchedTitle)) {
      row.status = 'review'
      row.genre = null
      row.genreName = null
      row.styles = []
      row.reviewReason = 'version_mismatch'
    }
  }
}
if (process.argv.includes('--renormalize')) {
  for (const row of rows) {
    if (!row.evidence?.rawGenre) continue
    const mapped = classification(row.evidence.rawGenre)
    Object.assign(row, mapped, { status: mapped.genre ? 'matched' : 'review', reviewReason: mapped.genre ? null : 'unmapped_genre' })
  }
  save(rows)
  console.log(`Renormalized ${rows.filter(row => row.status === 'matched').length}/${rows.length} songs`)
  process.exit(0)
}
save(rows)
let processed = 0
for (const [index, entry] of manifest.entries()) {
  if (rows[index].status !== 'pending' && rows[index].status !== 'search_error' && !(retryReview && rows[index].status === 'review')) continue
  if (maxSongs && processed >= maxSongs) break
  try {
    const country = store[entry.category] || 'US'
    let searchResult = await search(`${entry.title} ${entry.artist}`, country)
    let hit = bestMatch(entry, searchResult.results)
    if (!hit && normal(entry.title).length >= 3) {
      searchResult = await search(entry.title, country)
      hit = bestMatch(entry, searchResult.results)
    }
    if (hit) {
      const mapped = classification(hit.item.primaryGenreName)
      const confidence = hit.title >= 0.94 && hit.artist >= 0.9 ? 'high' : 'medium'
      rows[index] = {
        sourcePath: `${entry.category}/${entry.file}`, title: entry.title, artist: entry.artist, language: entry.category,
        ...mapped, status: mapped.genre ? 'matched' : 'review', confidence,
        reviewReason: mapped.genre ? null : 'unmapped_genre',
        evidence: {
          provider: 'Apple iTunes Search API', rawGenre: hit.item.primaryGenreName,
          matchedTitle: hit.item.trackName, matchedArtist: hit.item.artistName,
          titleSimilarity: Number(hit.title.toFixed(3)), artistSimilarity: Number(hit.artist.toFixed(3)),
          trackId: hit.item.trackId, trackUrl: hit.item.trackViewUrl || null, searchUrl: searchResult.url,
        },
      }
    } else {
      rows[index] = { ...rows[index], status: 'review', confidence: null, reviewReason: 'no_confident_match',
        evidence: { provider: 'Apple iTunes Search API', searchUrl: searchResult.url, resultCount: searchResult.results.length } }
    }
  } catch (error) {
    rows[index] = { ...rows[index], status: 'search_error', confidence: null, reviewReason: 'search_error', evidence: { error: String(error.message || error) } }
  }
  processed++
  if (processed % 10 === 0 || (maxSongs && processed === maxSongs)) {
    save(rows)
    const matched = rows.filter(row => row.status === 'matched').length
    console.log(`Processed ${processed}; matched ${matched}; remaining ${rows.filter(row => row.status === 'pending').length}`)
  }
}
save(rows)
console.log(`Finished this pass: ${processed} songs; ${rows.filter(row => row.status === 'matched').length}/${rows.length} classified`)
