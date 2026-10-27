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

在 IDEA 中直接运行 `MusicApplication` 时，将运行配置的“工作目录”设为 `D:\Java\muc\code`（或项目根目录 `D:\Java\muc`）；后端会自动读取项目根目录的 `.env`。IDEA 不会自行加载 `.env`，如工作目录设在其他位置，请在“环境变量”中设置 `DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL` 和数据库连接变量。修改配置后重启后端。

最初导入的 504 条记录中，43 个源 `.mp3` 实为 HTML 下载错误页。现已删除这 43 个本地文件及其对应封面、清单条目、数据库记录和 MinIO 对象；曲库保留 461 首可播放歌曲。歌曲 504 设置为 VIP，可用于验证 30 秒试听。已删除素材的历史路径保存在清单中。首次导入时才需将 `IMPORT_LOCAL_MUSIC=true`；当前 `.env` 已改回 `false`。
