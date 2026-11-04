package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 recommendations：按用户、歌曲、算法保存的推荐分数与原因。 */
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
