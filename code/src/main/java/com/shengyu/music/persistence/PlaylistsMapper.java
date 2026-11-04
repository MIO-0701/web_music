package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface PlaylistsMapper extends BaseMapper<Playlists> {
    @Select("SELECT p.id,p.name,p.description,p.created_at,COUNT(visible.id) song_count," +
        "(SELECT ps2.song_id FROM playlist_songs ps2 JOIN songs s2 ON s2.id=ps2.song_id AND s2.published=TRUE WHERE ps2.playlist_id=p.id ORDER BY ps2.added_at DESC,ps2.song_id DESC LIMIT 1) cover_song_id " +
        "FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlist_id=p.id LEFT JOIN songs visible ON visible.id=ps.song_id AND visible.published=TRUE " +
        "WHERE p.user_id=#{userId} GROUP BY p.id,p.name,p.description,p.created_at ORDER BY p.id DESC")
    List<Map<String,Object>> listForUser(long userId);
}
