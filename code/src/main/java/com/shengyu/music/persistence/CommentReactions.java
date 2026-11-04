package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("comment_reactions")
public class CommentReactions {
    @TableField("comment_id")
    public Long commentId;
    @TableField("user_id")
    public Long userId;
    @TableField("value")
    public Integer value;
}
