# 歌曲批量管理与上传

管理员可在“歌曲管理”页面搜索歌名、歌手或专辑，勾选歌曲后批量下架、删除、设为 VIP 或取消 VIP。当前页可全选，选择可跨页保留，每次最多处理 100 首。设为 VIP 会先通过 FFmpeg 生成 30 秒独立试听文件。删除歌曲时，数据库关联记录会级联删除；数据库提交后尝试清理 MinIO 对象，清理失败会写入后端日志。

## 批量上传

页面的“下载 CSV 表格模板”按钮会下载 UTF-8 模板。准备两个 ZIP：一个只放 MP3，另一个放 JPG、JPEG、PNG 或 WebP 封面。CSV 的 `audio_file` 和 `cover_file` 应写对应 ZIP 内的相对路径；例如 `cn/song.mp3` 和 `cn/cover.jpg`。请替换或删除模板中的示例行。

CSV 表头固定为：

```csv
title,artist,album,audio_file,cover_file,language,genre,vip
示例歌曲,示例歌手,示例专辑,cn/song.mp3,cn/cover.jpg,cn,pop,false
```

`title` 为歌名，`artist` 为歌手，`album` 为专辑。`language` 填 `cn`（中文）、`en`（英文）、`jp`（日文）、`fr`（法文）或 `ru`（俄文）；`vip` 填 `true` 或 `false`。文件名和路径必须与 ZIP 内完全一致。

`genre` 可选代码：

| 代码 | 分类 | 代码 | 分类 |
| --- | --- | --- | --- |
| `pop` | 流行 | `rock` | 摇滚 |
| `alternative` | 另类 | `rnb-soul` | R&B / 灵魂 |
| `hip-hop` | 嘻哈 / 说唱 | `electronic` | 电子 |
| `dance` | 舞曲 | `jazz` | 爵士 |
| `classical` | 古典 | `folk` | 民谣 |
| `singer-songwriter` | 唱作 | `soundtrack` | 原声 / 配乐 |
| `anime` | 动漫音乐 | `country` | 乡村 |
| `metal` | 金属 | `blues` | 蓝调 |
| `reggae` | 雷鬼 | `latin` | 拉丁 |
| `instrumental` | 器乐 | `new-age` | 新世纪 |

每个 ZIP 不超过 400 MB，CSV 不超过 1 MB，单次最多 100 首。ZIP 内每首 MP3 不超过 50 MB、每张封面不超过 10 MB，解压总量不超过 1 GB。重复音频内容会跳过，其他失败会按 CSV 行号返回。

## 管理员接口

| 接口 | 作用 |
| --- | --- |
| `GET /api/admin/songs?keyword=&page=1&size=10` | 分页搜索歌曲。 |
| `POST /api/admin/songs/batch/actions` | 请求体 `{"ids":[1,2],"action":"UNPUBLISH"}`。`action` 可为 `UNPUBLISH`、`DELETE`、`SET_VIP`、`CLEAR_VIP`。 |
| `GET /api/admin/songs/batch/template` | 下载 CSV 模板。 |
| `POST /api/admin/songs/batch/upload` | 表单字段 `audioZip`、`coverZip`、`csv`，返回 `total`、`imported`、`skipped`、`failed`、`ids`、`errors`。 |
