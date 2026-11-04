package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

@TableName("playlist_songs")
public class PlaylistSongs {
    @TableField("playlist_id")
    public Long playlistId;
    @TableField("song_id")
    public Long songId;
    @TableField("added_at")
    public java.time.LocalDateTime addedAt;
}
