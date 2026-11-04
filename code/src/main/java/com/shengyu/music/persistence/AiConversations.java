package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("ai_conversations")
public class AiConversations {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("user_id")
    public Long userId;
    @TableField("title")
    public String title;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
    @TableField("updated_at")
    public java.time.LocalDateTime updatedAt;
}
