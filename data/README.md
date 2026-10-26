# 推荐实验数据

本机的 `hetrec2011-lastfm-2k.zip` 下载自 [GroupLens HetRec 2011](https://grouplens.org/datasets/hetrec-2011/)。原始 ZIP 被 Git 忽略；在其他机器上运行实验时，请从官方页面下载并放到本目录，或设置 `MUSIC_EXPERIMENT_DATASET_PATH`。数据集仅用于算法实验，系统播放的音频来自 `music/`。

`invalid-audio.csv` 保留 43 个扩展名为 `.mp3`、实际内容为 HTML 的素材路径，供追溯使用。这些本地文件及对应封面、封面清单条目、数据库记录和 MinIO 对象均已删除；若以后取得真实音频，可以重新导入。
