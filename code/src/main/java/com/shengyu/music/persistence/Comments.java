package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("comments")
public class Comments {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("user_id")
    public Long userId;
    @TableField("song_id")
    public Long songId;
    @TableField("content")
    public String content;
    @TableField("status")
    public String status;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
}
