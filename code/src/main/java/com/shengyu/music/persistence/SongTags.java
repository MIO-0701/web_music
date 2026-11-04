package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("song_tags")
public class SongTags {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("name")
    public String name;
}
