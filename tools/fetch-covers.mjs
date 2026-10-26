import fs from 'node:fs/promises';
import path from 'node:path';

const root = 'D:/Java/muc';
const musicRoot = path.join(root, 'music');
const imageRoot = path.join(root, 'img');
const manifestPath = path.join(imageRoot, '_cover-manifest.json');
const categories = ['cn', 'en', 'fr', 'jp', 'ru'];
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

function normalize(s) {
  return s.normalize('NFKC').toLowerCase().replace(/&/g, ' and ')
    .replace(/[^\p{L}\p{N}]+/gu, '');
}

function artistParts(s) {
  return s.split(/&|、|,| feat\.? | featuring | x /i)
    .map(normalize).filter(Boolean);
}

function similarity(a, b) {
  a = normalize(a); b = normalize(b);
  if (a === b) return 1;
  if (!a || !b) return 0;
  if (a.includes(b) || b.includes(a)) return Math.min(a.length, b.length) / Math.max(a.length, b.length);
  const row = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let prev = row[0]; row[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const old = row[j];
      row[j] = Math.min(row[j] + 1, row[j - 1] + 1, prev + (a[i - 1] === b[j - 1] ? 0 : 1));
      prev = old;
    }
  }
  return 1 - row[b.length] / Math.max(a.length, b.length);
}

function score(track, artist, result) {
  const title = similarity(track, result.trackName || '');
  const parts = artistParts(artist);
  const resultParts = artistParts(result.artistName || '');
  const artistScore = Math.max(0, ...parts.flatMap(p => resultParts.map(q =>
    p === q ? 1 : p.includes(q) || q.includes(p)
      ? Math.min(p.length, q.length) / Math.max(p.length, q.length)
      : similarity(p, q))));
  const total = title * 0.72 + artistScore * 0.28;
  return { title: +title.toFixed(3), artist: +artistScore.toFixed(3), total: +total.toFixed(3) };
}

async function fetchJson(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

async function findCover(category, name) {
  const stem = name.replace(/\.mp3$/i, '');
  const split = stem.lastIndexOf(' - ');
  const title = stem.slice(0, split), artist = stem.slice(split + 3);
  const term = `${title} ${artistParts(artist)[0] || artist}`;
  const url = `https://music.163.com/api/cloudsearch/pc?s=${encodeURIComponent(term)}&type=1&limit=30`;
  const data = await fetchJson(url);
  const candidates = (data.result?.songs || []).filter(x => x.al?.picUrl)
    .map(x => ({ track: x.name, artist: (x.ar || []).map(a => a.name).join(' & '), album: x.al?.name,
      url: x.al.picUrl.replace(/^http:/, 'https:'), source: `https://music.163.com/#/song?id=${x.id}`,
      score: score(title, artist, { trackName: x.name, artistName: (x.ar || []).map(a => a.name).join(' & ') }) }))
    .sort((a, b) => b.score.total - a.score.total).slice(0, 3);
  const best = candidates[0];
  const accepted = best && best.score.title >= 0.85 && best.score.artist >= 0.7 && best.score.total >= 0.85;
  return { category, file: name, title, artist, status: accepted ? 'matched' : 'review', candidates };
}

async function saveManifest(items) {
  const temp = `${manifestPath}.tmp`;
  await fs.writeFile(temp, JSON.stringify(items, null, 2) + '\n', 'utf8');
  await fs.rename(temp, manifestPath);
}

async function scan() {
  await fs.mkdir(imageRoot, { recursive: true });
  let items = [];
  try { items = JSON.parse(await fs.readFile(manifestPath, 'utf8')); } catch {}
  const index = new Map(items.map(x => [`${x.category}/${x.file}`, x]));
  const pending = [];
  for (const category of categories) {
    for (const name of await fs.readdir(path.join(musicRoot, category))) {
      if (!name.toLowerCase().endsWith('.mp3')) continue;
      if (!index.has(`${category}/${name}`)) pending.push([category, name]);
    }
  }
  console.log(`Scanning ${pending.length} songs; ${items.length} previously checked.`);
  let next = 0, done = 0;
  async function worker() {
    while (next < pending.length) {
      const [category, name] = pending[next++];
      let item;
      try { item = await findCover(category, name); }
      catch (e) { item = { category, file: name, status: 'error', error: e.message }; }
      items.push(item);
      done++;
      if (done % 20 === 0 || done === pending.length) {
        await saveManifest(items);
        console.log(`${done}/${pending.length}: matched ${items.filter(x => x.status === 'matched').length}, review ${items.filter(x => x.status === 'review').length}, errors ${items.filter(x => x.status === 'error').length}`);
      }
      await delay(250);
    }
  }
  await Promise.all([worker(), worker(), worker()]);
  await saveManifest(items);
}

async function download() {
  const items = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  let done = 0, failed = 0;
  for (const item of items) {
    if (item.status !== 'matched' || !item.candidates?.[0]) continue;
    const folder = path.join(imageRoot, item.category);
    const stem = item.file.replace(/\.mp3$/i, '');
    let exists = false;
    for (const ext of ['.jpg', '.png', '.webp']) {
      const output = path.join(folder, stem + ext);
      try { if ((await fs.stat(output)).size > 1000) { item.image = `${item.category}/${stem}${ext}`; exists = true; break; } } catch {}
    }
    if (exists) continue;
    const url = `${item.candidates[0].url}?param=600y600`;
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(20000) });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const bytes = Buffer.from(await response.arrayBuffer());
      const ext = bytes[0] === 0xff && bytes[1] === 0xd8 ? '.jpg'
        : bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) ? '.png'
        : bytes.toString('ascii', 0, 4) === 'RIFF' && bytes.toString('ascii', 8, 12) === 'WEBP' ? '.webp' : null;
      if (bytes.length < 1000 || !ext) throw new Error('Unrecognized image format');
      const output = path.join(folder, stem + ext);
      await fs.mkdir(folder, { recursive: true });
      await fs.writeFile(output, bytes);
      item.image = `${item.category}/${path.basename(output)}`;
      delete item.downloadError;
      done++;
    } catch (e) { item.downloadError = e.message; failed++; }
    if ((done + failed) % 20 === 0) { await saveManifest(items); console.log(`downloaded ${done}, failed ${failed}`); }
    await delay(80);
  }
  await saveManifest(items);
  console.log(`Done: downloaded ${done}, failed ${failed}`);
}

async function reclassify() {
  const items = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  for (const item of items) {
    if (!item.candidates?.length) continue;
    for (const candidate of item.candidates) candidate.score = score(item.title, item.artist,
      { trackName: candidate.track, artistName: candidate.artist });
    item.candidates.sort((a, b) => b.score.total - a.score.total);
    const best = item.candidates[0];
    item.status = best.score.title >= 0.85 && best.score.artist >= 0.7 && best.score.total >= 0.85 ? 'matched' : 'review';
  }
  await saveManifest(items);
  console.log(`matched ${items.filter(x => x.status === 'matched').length}; review ${items.filter(x => x.status === 'review').length}`);
}

async function qqFallback() {
  const items = JSON.parse(await fs.readFile(manifestPath, 'utf8'));
  const pending = items.filter(x => x.status === 'review' && x.file !== 'null - 未知歌手.mp3');
  let done = 0;
  for (const item of pending) {
    const term = `${item.title} ${artistParts(item.artist)[0] || item.artist}`;
    const url = `https://c.y.qq.com/soso/fcgi-bin/client_search_cp?format=json&p=1&n=30&w=${encodeURIComponent(term)}`;
    try {
      const data = await fetchJson(url);
      const candidates = (data.data?.song?.list || []).filter(x => x.albummid)
        .map(x => ({ track: x.songname, artist: (x.singer || []).map(s => s.name).join(' & '),
          album: x.albumname, url: `https://y.gtimg.cn/music/photo_new/T002R800x800M000${x.albummid}.jpg`,
          source: `https://y.qq.com/n/ryqq/songDetail/${x.songmid}`,
          score: score(item.title, item.artist, { trackName: x.songname, artistName: (x.singer || []).map(s => s.name).join(' & ') }) }))
        .sort((a, b) => b.score.total - a.score.total).slice(0, 3);
      if (candidates[0] && candidates[0].score.title >= 0.85 && candidates[0].score.artist >= 0.7 && candidates[0].score.total >= 0.85) {
        item.candidates = candidates;
        item.status = 'matched';
      } else item.qqCandidates = candidates;
    } catch (e) { item.qqError = e.message; }
    done++;
    if (done % 10 === 0 || done === pending.length) {
      await saveManifest(items);
      console.log(`${done}/${pending.length}: matched ${items.filter(x => x.status === 'matched').length}`);
    }
    await delay(250);
  }
  await saveManifest(items);
}

if (process.argv[2] === 'scan') await scan();
else if (process.argv[2] === 'download') await download();
else if (process.argv[2] === 'reclassify') await reclassify();
else if (process.argv[2] === 'qq') await qqFallback();
else throw new Error('Usage: node tools/fetch-covers.mjs scan|download');
