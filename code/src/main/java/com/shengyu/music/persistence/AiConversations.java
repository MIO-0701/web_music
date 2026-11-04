package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 ai_conversations：用户与 AI 的会话标题及更新时间。 */
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
