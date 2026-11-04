package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("song_tag_links")
public class SongTagLinks {
    @TableField("song_id")
    public Long songId;
    @TableField("tag_id")
    public Long tagId;
}
