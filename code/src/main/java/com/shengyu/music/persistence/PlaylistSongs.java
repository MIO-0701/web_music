package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 playlist_songs：歌单包含的歌曲及加入时间。 */
@TableName("playlist_songs")
public class PlaylistSongs {
    @TableField("playlist_id")
    public Long playlistId;
    @TableField("song_id")
    public Long songId;
    @TableField("added_at")
    public java.time.LocalDateTime addedAt;
}
