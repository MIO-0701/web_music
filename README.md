# 声屿音乐

基于 JDK 21、Spring Boot、Vue 3、MySQL、Redis、MinIO 和 DeepSeek 的音乐播放与推荐系统。

- 后端：[`code/README.md`](code/README.md)
- 前端：[`ui/README.md`](ui/README.md)
- 数据库设计：[`docs/architecture.md`](docs/architecture.md)
- 推荐算法与实验：[`docs/recommendation.md`](docs/recommendation.md)
- 测试用例：[`docs/test-cases.md`](docs/test-cases.md)
- 后端接口：[`docs/api.md`](docs/api.md)
- 无效音频清单：[`data/invalid-audio.csv`](data/invalid-audio.csv)

本机运行：先执行 `docker compose up -d` 启动依赖，再执行 `./tools/start-local.ps1` 启动后端。配置写在项目根目录的 `.env`（参照 `.env.example`）；Docker MySQL 映射到本机 3307，后端接口为 `http://localhost:8080`。停止后端使用 `./tools/stop-local.ps1`。前端在 `ui/` 执行 `npm run dev`；静态设计稿保留在 `http://localhost:5173/?prototype=1`。

目前导入了 504 条匹配记录，其中 43 个源 `.mp3` 实为 HTML 下载错误页，已从公开曲库下架；公开曲库有 461 首可播放歌曲。歌曲 504 设置为 VIP，可用于验证 30 秒试听。无效素材路径见清单，替换为真实 MP3 后方可重新入库。首次导入时才需将 `IMPORT_LOCAL_MUSIC=true`；当前 `.env` 已改回 `false`。
