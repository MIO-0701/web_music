package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 favorites：用户收藏歌曲的关联关系。 */
@TableName("favorites")
public class Favorites {
    @TableField("user_id")
    public Long userId;
    @TableField("song_id")
    public Long songId;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
}
