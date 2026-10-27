# 数据文件

本机的 `hetrec2011-lastfm-2k.zip` 下载自 [GroupLens HetRec 2011](https://grouplens.org/datasets/hetrec-2011/)。原始 ZIP 被 Git 忽略；在其他机器上运行实验时，请从官方页面下载并放到本目录，或设置 `MUSIC_EXPERIMENT_DATASET_PATH`。数据集仅用于算法实验，系统播放的音频来自 `music/`。

`invalid-audio.csv` 保留 43 个扩展名为 `.mp3`、实际内容为 HTML 的素材路径，供追溯使用。这些本地文件及对应封面、封面清单条目、数据库记录和 MinIO 对象均已删除；若以后取得真实音频，可以重新导入。

## 歌曲曲风分类

`song-genres.json` 按 `sourcePath` 对应 `songs.source_path`，记录当前有效歌曲的语言、归一化曲风 `genre`、中文名称 `genreName`、细分类 `styles`、匹配状态和证据。`evidence.rawGenre` 保存 Apple 原始 `primaryGenreName`，`evidence.trackUrl` 可用于人工核对。`matched` 代表曲名与歌手通过自动匹配，`review` 代表缺少可靠匹配或曲风无法归一化，`search_error` 代表检索失败。待复核歌曲的 `genre` 保持 `null`，不按语言猜测曲风。

本次对 461 首有效本地素材完成检索：403 首为 `matched`，58 首为 `review`。JSON 是离线分类资料，尚未写入 MySQL 的 `songs.genre` 或 `song_tags`；这样可以先复核曲风与版本，再决定正式标签。

分类脚本为 `node tools/classify-genres.mjs`；支持 `--limit=5` 抽样、`--retry-review` 重新检索待复核歌曲，以及 `--renormalize` 不联网重算原始曲风。完成后执行 `node tools/validate-song-genres.mjs` 核对有效素材、重复路径和来源字段。脚本按 [Apple Search API 公开说明](https://performance-partners.apple.com/search-api) 控制约每分钟 20 次请求，处理过程中定期保存 JSON，可中断后续跑。`zh-variants.json` 为简繁字形映射，用于核对中文曲名与歌手；它本身不提供曲风。曲风字段属于平台目录分类，可能无法反映歌曲的全部音乐特征，后续导入 `song_tags` 前应先人工审查 `review` 和低置信度记录。
