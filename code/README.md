# 声屿音乐后端

JDK 21 + Spring Boot 3.5，提供曲库、会员、播放记录、歌单、评论、推荐和 DeepSeek 查歌接口。前端位于 `../ui`。

## 本机运行

1. 安装 JDK 21、Maven、Docker Desktop。在项目根目录执行 `docker compose up -d`，启动 MySQL、Redis、MinIO。本机 MySQL 使用 3307，避免与已有的 3306 服务冲突。
2. 复制项目根目录 `.env.example` 为 `.env`，设置不少于 12 位的管理员密码。使用 AI 查歌时设置 `DEEPSEEK_API_KEY`；模型通过 `DEEPSEEK_MODEL=deepseek-flash` 指定。`.env` 已被 Git 忽略，不要把真实密钥放进示例文件或提交记录。
3. 执行 `./tools/start-local.ps1`。该脚本读取根目录 `.env`、设置本机数据库地址和素材目录，并将日志写入 `code/.runtime/`。停止使用 `./tools/stop-local.ps1`。首次启动由 Flyway 建表，并创建管理员。
4. 首次导入本地素材时设置 `IMPORT_LOCAL_MUSIC=true`，或执行 `./tools/start-local.ps1 -Import`。导入完成后改回 `false`。导入读取 `../img/_cover-manifest.json`，按 `source_path` 幂等写入 MinIO。
5. 前端执行 `npm install`、`npm run dev`。后端端口为 8080，MinIO 控制台为 9001。接口清单见 [`../docs/api.md`](../docs/api.md)。

最初导入的 504 条匹配记录中有 43 个伪 MP3（内容为 HTML）。这 43 个本地音频及对应封面、清单条目、数据库记录和 MinIO 对象已删除；当前曲库为 461 首。两条 `review` 记录原本就未导入。历史无效文件清单为 [`../data/invalid-audio.csv`](../data/invalid-audio.csv)。上传接口会拒绝这类文件；若以后取得真实音频，可重新导入。歌曲 504 已设置为 VIP，可测试 30 秒独立试听。

可选：`docker compose --profile full up -d --build` 将后端也放入容器，并只读挂载素材和实验数据目录。此方式需能访问 Docker Hub 的 Maven/JRE 基础镜像。MinIO 官方社区版改为源码发行后，本项目固定使用 [coollabsio 的 MinIO 源码构建镜像](https://github.com/coollabsio/minio)；相关背景见 [MinIO 项目说明](https://github.com/minio/minio/blob/master/README.md)。

若本机未在 PATH 中提供 Maven，可使用 IntelliJ IDEA 自带的 Maven。手动运行 Spring Boot 时请设置 `DB_URL=jdbc:mysql://localhost:3307/shengyu?...`；启动脚本已处理。前端默认显示真实接口页面；访问 `?prototype=1` 可查看保留的静态设计稿。

创建或改为 VIP 歌曲时，需要 FFmpeg 生成独立的 30 秒试听音频。当前本机的可携带版在 `../tools/ffmpeg/`，启动脚本会自动找到；其他环境可通过 `FFMPEG_BIN` 指向可执行文件。首次导入的有效歌曲默认为免费且已上架；管理员可在后台调整标签、类型和状态。

## 安全与数据

- 注册只创建 `USER`。管理员只由启动环境变量初始化，密码使用 BCrypt。登录令牌为 32 字节随机值，存于 Redis，有效期 7 天；用户状态与会员有效期在每次请求时重新读取。
- MinIO 桶不公开；音频由后端按 Range 流式读取。未到期会员可完整播放 VIP 歌曲，普通用户只会读取独立试听对象。封面接口可公开读取已上架歌曲的封面。
- 播放事件至少 10 秒，每个用户与歌曲 30 秒内去重。推荐每天 06:00（北京时间）重算；管理端可手动重算。AI 每人每天最多 30 次，查询条件会由服务端校验，结果只来自本地已上架歌曲。

## 主要接口

| 分组 | 接口 |
| --- | --- |
| 认证 | `POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`、`POST /api/auth/logout` |
| 曲库 | `GET /api/songs`、`GET /api/songs/hot`、`GET /api/songs/{id}`、`GET /api/songs/{id}/cover`、`GET /api/songs/{id}/audio` |
| 用户 | `GET/POST/DELETE /api/me/favorites`、`/api/me/playlists`、`GET /api/me/history`、`POST /api/me/play-events` |
| 评论 | `GET/POST /api/songs/{id}/comments` |
| 推荐 | `GET /api/me/recommendations?algorithm=TAG\|ITEM_CF` |
| AI | `POST /api/me/ai/chat`、`GET /api/me/ai/conversations`、`GET /api/me/ai/conversations/{id}/messages` |
| 管理 | `/api/admin/users`、`/api/admin/songs`、`/api/admin/comments`、`/api/admin/dashboard`、`POST /api/admin/recommendations/rebuild` |

离线实验使用 [GroupLens HetRec 2011 Last.fm 2K](https://grouplens.org/datasets/hetrec-2011/) 的 ZIP 文件。将文件放到 `../data/hetrec2011-lastfm-2k.zip` 后，管理员调用 `POST /api/admin/recommendations/evaluate`。下载来源、采样与指标定义见 `../docs/recommendation.md`。

列表接口支持 `page`（从 1 开始）与 `size`（最多 100）。除公开曲库和评论读取、注册与登录外，接口需要 `Authorization: Bearer <token>`。管理接口仅允许 `ADMIN`。
