package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/** 持久层 Mapper：连接 MySQL shengyu 库的 playlist_songs：歌单包含的歌曲及加入时间。 提供该表的数据访问方法。 */
@Mapper
public interface PlaylistSongsMapper extends BaseMapper<PlaylistSongs> {
    @Insert("INSERT IGNORE INTO playlist_songs(playlist_id,song_id) VALUES(#{playlistId},#{songId})")
    int insertIgnore(@Param("playlistId") long playlistId, @Param("songId") long songId);
    @Select("SELECT s.id,s.title,s.artist,s.album_name,s.genre,s.duration_seconds,s.vip FROM playlist_songs ps JOIN songs s ON s.id=ps.song_id WHERE ps.playlist_id=#{playlistId} AND s.published=TRUE ORDER BY ps.added_at DESC")
    List<Map<String,Object>> visibleSongs(long playlistId);
}
