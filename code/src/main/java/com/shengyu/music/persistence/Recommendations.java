package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("recommendations")
public class Recommendations {
    @TableField("user_id")
    public Long userId;
    @TableField("song_id")
    public Long songId;
    @TableField("algorithm")
    public String algorithm;
    @TableField("score")
    public Double score;
    @TableField("reason")
    public String reason;
    @TableField("generated_at")
    public java.time.LocalDateTime generatedAt;
}
