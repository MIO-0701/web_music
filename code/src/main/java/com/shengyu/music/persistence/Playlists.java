package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("playlists")
public class Playlists {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("user_id")
    public Long userId;
    @TableField("name")
    public String name;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
    @TableField("description")
    public String description;
}
