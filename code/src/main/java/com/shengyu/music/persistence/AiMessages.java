package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("ai_messages")
public class AiMessages {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("conversation_id")
    public Long conversationId;
    @TableField("role")
    public String role;
    @TableField("content")
    public String content;
    @TableField("parsed_conditions")
    public String parsedConditions;
    @TableField("result_song_ids")
    public String resultSongIds;
    @TableField("status")
    public String status;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
}
