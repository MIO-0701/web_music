package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 playlists：用户创建的歌单及其名称。 */
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
