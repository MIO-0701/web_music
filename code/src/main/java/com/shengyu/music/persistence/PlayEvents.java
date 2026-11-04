package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("play_events")
public class PlayEvents {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("user_id")
    public Long userId;
    @TableField("song_id")
    public Long songId;
    @TableField("seconds_played")
    public Integer secondsPlayed;
    @TableField("completed")
    public Boolean completed;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
}
