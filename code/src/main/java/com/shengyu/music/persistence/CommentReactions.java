package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 comment_reactions：用户对评论的互动记录。 */
@TableName("comment_reactions")
public class CommentReactions {
    @TableField("comment_id")
    public Long commentId;
    @TableField("user_id")
    public Long userId;
    @TableField("value")
    public Integer value;
}
