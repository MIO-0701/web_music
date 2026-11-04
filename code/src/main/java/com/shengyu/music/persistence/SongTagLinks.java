package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 song_tag_links：歌曲与标签的多对多关系。 */
@TableName("song_tag_links")
public class SongTagLinks {
    @TableField("song_id")
    public Long songId;
    @TableField("tag_id")
    public Long tagId;
}
