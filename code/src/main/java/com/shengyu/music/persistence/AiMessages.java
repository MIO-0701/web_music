package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 ai_messages：会话消息、解析条件与匹配歌曲结果。 */
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
