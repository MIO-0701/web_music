package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 songs：歌曲元数据、发布和会员状态，以及 MinIO 媒体对象键。 */
@TableName("songs")
public class Songs {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("title")
    public String title;
    @TableField("artist")
    public String artist;
    @TableField("album_name")
    public String albumName;
    @TableField("language_code")
    public String languageCode;
    @TableField("genre")
    public String genre;
    @TableField("description")
    public String description;
    @TableField("audio_key")
    public String audioKey;
    @TableField("preview_key")
    public String previewKey;
    @TableField("cover_key")
    public String coverKey;
    @TableField("source_path")
    public String sourcePath;
    @TableField("duration_seconds")
    public Integer durationSeconds;
    @TableField("vip")
    public Boolean vip;
    @TableField("published")
    public Boolean published;
    @TableField("play_count")
    public Long playCount;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
    @TableField("updated_at")
    public java.time.LocalDateTime updatedAt;
}
