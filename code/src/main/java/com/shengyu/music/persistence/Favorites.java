package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("favorites")
public class Favorites {
    @TableField("user_id")
    public Long userId;
    @TableField("song_id")
    public Long songId;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
}
