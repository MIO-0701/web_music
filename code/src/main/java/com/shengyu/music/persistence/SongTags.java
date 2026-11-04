package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 song_tags：可复用的歌曲标签名称。 */
@TableName("song_tags")
public class SongTags {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("name")
    public String name;
}
