# 推荐实验数据

本机的 `hetrec2011-lastfm-2k.zip` 下载自 [GroupLens HetRec 2011](https://grouplens.org/datasets/hetrec-2011/)。原始 ZIP 被 Git 忽略；在其他机器上运行实验时，请从官方页面下载并放到本目录，或设置 `MUSIC_EXPERIMENT_DATASET_PATH`。数据集仅用于算法实验，系统播放的音频来自 `music/`。

`invalid-audio.csv` 列出 43 个扩展名为 `.mp3`、实际内容为 HTML 的素材。它们已在本机曲库下架，替换为真实音频后才能恢复播放。
