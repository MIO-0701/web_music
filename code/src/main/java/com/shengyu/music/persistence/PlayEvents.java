package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 play_events：用户的实际播放时长和完成状态，供历史与推荐使用。 */
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
