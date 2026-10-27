# 后端接口清单

本机地址：`http://localhost:8080`。表中的路径均以 `/api` 开头。标记为「登录」的接口使用请求头 `Authorization: Bearer <token>`；「管理员」还要求 `ADMIN` 角色。普通接口请求体为 JSON，歌曲上传为 `multipart/form-data`。

本机验收数据：曲库 461 首；歌曲 `504` 为 VIP，适合测试普通用户试听与会员完整播放。最初导入的 43 条无效音频及对应封面已从本地、MinIO 和数据库删除；历史路径见 [`../data/invalid-audio.csv`](../data/invalid-audio.csv)。管理员账号由根目录 `.env` 初始化，不要将真实令牌写进测试报告。

注册与登录返回 `token`、`tokenType`、`expiresIn`（秒）和 `user`。分页结果统一为 `{ "items": [], "total": 0, "page": 1, "size": 20 }`；`page` 从 1 开始，`size` 限制在 1–100。错误响应为 `{ "error": "原因" }`，常见状态码：400 参数错误、401 未登录或登录失败、403 无权限、404 资源不存在或已下架、409 数据冲突、429 AI 配额超限、503 依赖服务不可用。

## 认证与账户

| 方法与路径 | 权限 | 作用及主要参数 |
| --- | --- | --- |
| `POST /api/auth/register` | 公开 | 注册普通用户并直接登录。JSON：`username`（3–30 位字母、数字或 `_`）、`password`（8–72 位）、可选 `displayName`。重复用户名返回 409。 |
| `POST /api/auth/login` | 公开 | 密码登录。JSON：`username`、`password`。令牌有效期 7 天。 |
| `GET /api/auth/me` | 登录 | 读取当前用户 `id`、`username`、`displayName`、`role`、`membershipExpiresAt`。 |
| `POST /api/auth/logout` | 登录 | 撤销当前 Bearer 令牌。 |

## 公开曲库与播放

| 方法与路径 | 权限 | 作用及主要参数 |
| --- | --- | --- |
| `GET /api/songs` | 公开 | 已上架曲库检索。可选 `keyword`（歌名、歌手、专辑模糊搜索）、`genre`、`language`（当前素材使用 `cn/en/fr/jp/ru`）、`vip=true/false`、`page`、`size`。 |
| `GET /api/songs/genres` | 公开 | 返回已上架歌曲的非空曲风及对应 `song_count`，供前端风格筛选使用。 |
| `GET /api/songs/hot` | 公开 | 获取按播放量排序的前 12 首已上架歌曲；Redis 缓存 10 分钟，播放和管理变更时主动失效。 |
| `GET /api/songs/{id}` | 公开 | 已上架歌曲详情，含 `tags`、`coverUrl`、`audioUrl`。已下架返回 404。 |
| `GET /api/songs/{id}/cover` | 公开 | 返回封面二进制；无封面或已下架返回 404。 |
| `GET /api/songs/{id}/audio` | 登录 | 返回 MP3。支持单段 `Range: bytes=0-1023` 或 `bytes=-1024`，部分内容返回 206。非会员请求 VIP 曲目时返回独立 30 秒试听文件，会员及管理员返回完整音频；已下架返回 404。 |
| `GET /api/songs/{id}/comments` | 公开 | 查询已审核评论，支持 `page`、`size`。 |
| `POST /api/songs/{id}/comments` | 登录 | 提交评论，JSON：`content`（1–1000 字）。初始状态 `PENDING`，审核通过后公开显示。 |

歌曲 JSON 字段包括 `id`、`title`、`artist`、`album_name`、`language_code`、`genre`、`description`、`duration_seconds`、`vip`、`published`、`play_count`、`coverUrl`、`audioUrl`。封面与音频 URL 是站内相对路径。

## 用户音乐服务

| 方法与路径 | 权限 | 作用及主要参数 |
| --- | --- | --- |
| `GET /api/me/favorites` | 登录 | 分页查看已收藏且仍上架的歌曲；支持 `keyword`（歌名、歌手、专辑）、`page`、`size`。 |
| `POST /api/me/favorites/{songId}` | 登录 | 收藏已上架歌曲，重复操作幂等。 |
| `DELETE /api/me/favorites/{songId}` | 登录 | 取消收藏，重复操作幂等。 |
| `GET /api/me/playlists` | 登录 | 查看自己的歌单及 `song_count`、封面歌曲 ID `cover_song_id`。 |
| `POST /api/me/playlists` | 登录 | 创建歌单，JSON：`{"name":"夜间散步"}`，返回新歌单 `id`。 |
| `GET /api/me/playlists/{id}` | 登录 | 查看自己的歌单及其中已上架歌曲；其他人的歌单返回 404。 |
| `PUT /api/me/playlists/{id}` | 登录 | 重命名自己的歌单，JSON：`{"name":"新名称"}`。 |
| `DELETE /api/me/playlists/{id}` | 登录 | 删除自己的歌单。 |
| `POST /api/me/playlists/{id}/songs/{songId}` | 登录 | 将已上架歌曲加入自己的歌单，重复操作幂等。 |
| `DELETE /api/me/playlists/{id}/songs/{songId}` | 登录 | 从自己的歌单移除歌曲，重复操作幂等。 |
| `POST /api/me/play-events` | 登录 | 记录播放，JSON：`{"songId":1,"secondsPlayed":20,"completed":false}`。时长为 10–7200 秒，不能超过可播放范围约 3 秒；同一用户与歌曲 30 秒内去重。 |
| `GET /api/me/history` | 登录 | 分页读取播放历史；每首已上架歌曲只显示最近一次播放记录，支持 `page`、`size`。 |

## 推荐与 AI 查歌

| 方法与路径 | 权限 | 作用及主要参数 |
| --- | --- | --- |
| `GET /api/me/recommendations?algorithm=TAG` | 登录 | 标签规则推荐；也可传 `ITEM_CF` 获取隐式反馈协同过滤推荐。返回歌曲数组，含 `score`、`reason`；新用户返回热门歌曲。 |
| `GET /api/me/recommendations/daily` | 登录 | 前端每日推荐统一入口。昨日有至少 10 秒播放或新增收藏时，按昨日歌曲的语言、曲风和标签推荐未听过的已上架歌曲；否则按全站昨日播放次数推荐，播放数相同时按累计热度排序。返回最多 20 首，含 `score`、`reason`。 |
| `POST /api/me/ai/chat` | 登录 | 自然语言查歌。JSON：`{"conversationId":null,"message":"找几首日语歌"}`；追问时填写返回的 `conversationId`。返回 `conversationId`、`reply`、`conditions` 与本地上架歌曲 `songs`。每用户每天最多 30 次。 |
| `GET /api/me/ai/conversations` | 登录 | 最近 50 个会话，按更新时间倒序。 |
| `GET /api/me/ai/conversations/{id}/messages` | 登录 | 自己的会话消息，含解析条件、歌曲 ID、状态；其他人的会话返回 404。 |

AI 接口需要配置 `DEEPSEEK_API_KEY`。未配置时 `POST /api/me/ai/chat` 返回 503；失败的用户消息和错误消息仍会保存，调用也会计入当日配额。

## 管理端

| 方法与路径 | 权限 | 作用及主要参数 |
| --- | --- | --- |
| `GET /api/admin/users` | 管理员 | 分页查询用户。可选 `keyword`（用户名或昵称）、`page`、`size`。 |
| `PUT /api/admin/users/{id}/membership` | 管理员 | 设置普通用户会员到期时间。JSON：`{"expiresAt":"2026-12-31T23:59:59"}`；传 `null` 取消会员。必须晚于服务器当前时间。 |
| `PATCH /api/admin/users/{id}/enabled?value=false` | 管理员 | 禁用或启用普通用户；禁用后其现有令牌也不能继续访问。 |
| `GET /api/admin/songs` | 管理员 | 分页检索全部歌曲，含已下架；支持 `keyword`、`page`、`size`。 |
| `GET /api/admin/songs/{id}` | 管理员 | 查看歌曲详情，含已下架歌曲。 |
| `POST /api/admin/songs` | 管理员 | 上传并上架歌曲。`multipart/form-data`：必填 `audio`（MP3 文件）、`title`、`artist`；可选 `cover`（JPG/PNG/WebP）、`album`、`language`、`genre`、`vip`、重复 `tags`。返回新歌曲 `id`。 |
| `PUT /api/admin/songs/{id}` | 管理员 | 编辑歌曲。JSON：`title`、`artist`、`album`、`genre`、`description`、`vip`、`published`、`tags`（字符串数组）。这是完整编辑接口；提交时须带上当前 `vip` 和 `published`，否则布尔字段按 `false` 处理。首次改为 VIP 时生成独立试听音频。 |
| `PATCH /api/admin/songs/{id}/publish?value=false` | 管理员 | 歌曲下架或重新上架。下架后公开曲库、封面、音频和评论读取均受限。 |
| `GET /api/admin/comments` | 管理员 | 分页查看全部评论，可选 `status=PENDING/APPROVED/HIDDEN`、`page`、`size`。 |
| `PATCH /api/admin/comments/{id}/status?value=APPROVED` | 管理员 | 审核或隐藏评论，`value` 仅允许 `APPROVED`、`HIDDEN`。 |
| `GET /api/admin/dashboard` | 管理员 | 用户数、有效会员数、已上架歌曲数、今日播放数、待审评论数、热门歌曲、近七天播放量、曲风分布和近期实验。 |
| `POST /api/admin/recommendations/rebuild` | 管理员 | 为全部启用普通用户重算 `TAG` 与 `ITEM_CF` 推荐；返回 `usersProcessed`。定时任务每天北京时间 06:00 自动执行。 |
| `GET /api/admin/recommendations/experiments` | 管理员 | 查看最近 20 次离线推荐实验结果。 |
| `POST /api/admin/recommendations/evaluate` | 管理员 | 从配置的 HetRec 2011 Last.fm ZIP 运行固定切分实验，保存并返回两种算法的 Precision@10、Recall@10、Coverage。该实验是艺人级代理数据，不能直接代表歌曲级线上效果。 |

上传大小上限：音频单文件 50 MB、整个请求 60 MB。VIP 上传或将普通歌曲改为 VIP 需要后端运行环境有 FFmpeg。用户在写权限用例时，可分别使用未登录、普通用户、有效会员和管理员令牌。

## 公开接口调用示例

```powershell
$base = 'http://localhost:8080'
Invoke-RestMethod "$base/api/songs?language=cn&page=1&size=5"
```
