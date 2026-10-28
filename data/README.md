# 数据文件

本机的 `hetrec2011-lastfm-2k.zip` 下载自 [GroupLens HetRec 2011](https://grouplens.org/datasets/hetrec-2011/)。原始 ZIP 被 Git 忽略；在其他机器上运行实验时，请从官方页面下载并放到本目录，或设置 `MUSIC_EXPERIMENT_DATASET_PATH`。数据集仅用于算法实验，系统播放的音频来自 `music/`。

`invalid-audio.csv` 保留 43 个扩展名为 `.mp3`、实际内容为 HTML 的素材路径，供追溯使用。这些本地文件及对应封面、封面清单条目、数据库记录和 MinIO 对象均已删除；若以后取得真实音频，可以重新导入。

## 歌曲曲风分类

`song-genres.json` 按 `sourcePath` 对应 `songs.source_path`，记录当前有效歌曲的语言、归一化曲风 `genre`、中文名称 `genreName`、细分类 `styles`、匹配状态和证据。分类脚本从封面清单及 `music/` 目录发现歌曲；新增但未写入封面清单的音频也会列入 JSON。`evidence.rawGenre` 保存 Apple 原始 `primaryGenreName`，`evidence.trackUrl` 可用于人工核对。`matched` 代表曲名与歌手通过自动匹配，`review` 代表缺少可靠匹配或曲风无法归一化，`search_error` 代表检索失败。待复核歌曲的 `genre` 保持 `null`，不按语言猜测曲风。

目前共记录 568 首本地音频：482 首为 `matched`，86 首为 `review`。本次新增 107 首，其中 79 首已匹配曲风，28 首待复核；原有 461 首的结果未改动。后端启动时读取此 JSON，将 `matched` 记录的归一化曲风同步到 `songs.genre`，仅更新当前仍为“未分类”且 `source_path` 匹配的歌曲，不覆盖管理员手工设置。`review` 记录保留为“未分类”；`styles` 暂未导入 `song_tags`。直接从 IDEA 启动时可设置 `MUSIC_GENRE_CATALOG_PATH` 指向本文件；默认路径为后端工作目录的 `../data/song-genres.json`。

分类脚本为 `node tools/classify-genres.mjs`；支持 `--limit=5` 抽样、`--retry-review` 重新检索待复核歌曲、`--retry-unlisted` 仅复检未写入封面清单的歌曲，以及 `--renormalize` 不联网重算原始曲风。完成后执行 `node tools/validate-song-genres.mjs` 核对有效素材、重复路径和来源字段。脚本按 [Apple Search API 公开说明](https://performance-partners.apple.com/search-api) 控制约每分钟 20 次请求，处理过程中定期保存 JSON，可中断后续跑。`zh-variants.json` 为简繁字形映射，用于核对中文曲名与歌手；它本身不提供曲风。曲风字段属于平台目录分类，可能无法反映歌曲的全部音乐特征，后续导入 `song_tags` 前应先人工审查 `review` 和低置信度记录。
