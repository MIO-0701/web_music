package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 comments：歌曲评论正文、作者和审核状态。 */
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
