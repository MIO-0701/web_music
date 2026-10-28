import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'img', '_cover-manifest.json'), 'utf8'))
const catalog = JSON.parse(fs.readFileSync(path.join(root, 'data', 'song-genres.json'), 'utf8'))
const expected = new Set(manifest.filter(row => row.status === 'matched' && fs.existsSync(path.join(root, 'music', row.category, row.file)))
  .map(row => `${row.category}/${row.file}`))
for (const category of fs.readdirSync(path.join(root, 'music'), { withFileTypes: true }).filter(row => row.isDirectory()).map(row => row.name)) {
  for (const file of fs.readdirSync(path.join(root, 'music', category)).filter(file => file.toLowerCase().endsWith('.mp3'))) expected.add(`${category}/${file}`)
}
const paths = new Set()
for (const song of catalog.songs) {
  if (!expected.has(song.sourcePath)) throw new Error(`不在有效曲库中：${song.sourcePath}`)
  if (paths.has(song.sourcePath)) throw new Error(`重复歌曲：${song.sourcePath}`)
  paths.add(song.sourcePath)
  if (song.status === 'matched' && (!song.genre || !song.evidence?.rawGenre || !song.evidence?.trackUrl))
    throw new Error(`分类缺少证据：${song.sourcePath}`)
  if (song.status !== 'matched' && song.genre != null) throw new Error(`待复核歌曲存在未经验证的曲风：${song.sourcePath}`)
}
if (paths.size !== expected.size) throw new Error(`曲库数量不一致：JSON ${paths.size}，素材 ${expected.size}`)
if (catalog.songs.some(song => song.status === 'pending' || song.status === 'search_error'))
  throw new Error('仍有未检索完毕或网络失败的歌曲')
const counts = catalog.songs.reduce((acc, song) => { acc[song.status] = (acc[song.status] || 0) + 1; return acc }, {})
console.log(JSON.stringify({ total: paths.size, counts, genres: [...new Set(catalog.songs.map(song => song.genre).filter(Boolean))] }, null, 2))
