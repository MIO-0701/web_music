package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.Map;

@Mapper
public interface SongsMapper extends BaseMapper<Songs> {
    @Select("SELECT id,title,artist,album_name,genre,duration_seconds,vip,play_count score,'当前热门歌曲' reason FROM songs WHERE published=TRUE ORDER BY play_count DESC,id DESC LIMIT 20")
    List<Map<String,Object>> fallbackRecommendations();
    @Select("SELECT s.id,s.title,s.artist,s.album_name,s.genre,s.language_code,s.duration_seconds,s.vip,COALESCE(h.plays,0) score,'昨日热门歌曲' reason FROM songs s LEFT JOIN (SELECT song_id,COUNT(*) plays FROM play_events WHERE created_at>=CURDATE()-INTERVAL 1 DAY AND created_at<CURDATE() GROUP BY song_id) h ON h.song_id=s.id WHERE s.published=TRUE ORDER BY score DESC,s.play_count DESC,s.id DESC LIMIT 20")
    List<Map<String,Object>> yesterdayPopular();
    @Update("UPDATE songs SET play_count=play_count+1 WHERE id=#{id}")
    int incrementPlayCount(long id);
    @Select("SELECT genre,COUNT(*) count FROM songs WHERE published=TRUE GROUP BY genre ORDER BY count DESC LIMIT 8")
    List<Map<String,Object>> topGenres();
    @Select("<script>SELECT DISTINCT s.id,s.title,s.artist,s.album_name,s.genre,s.language_code,s.duration_seconds,s.vip,s.play_count " +
        "FROM songs s LEFT JOIN song_tag_links st ON st.song_id=s.id LEFT JOIN song_tags t ON t.id=st.tag_id WHERE s.published=TRUE " +
        "<if test='keyword != null and keyword != \"\"'>AND (s.title LIKE CONCAT('%',#{keyword},'%') OR s.artist LIKE CONCAT('%',#{keyword},'%') OR s.album_name LIKE CONCAT('%',#{keyword},'%') OR s.description LIKE CONCAT('%',#{keyword},'%') OR t.name LIKE CONCAT('%',#{keyword},'%'))</if> " +
        "<if test='genre != null and genre != \"\"'>AND (s.genre LIKE CONCAT('%',#{genre},'%') OR t.name LIKE CONCAT('%',#{genre},'%'))</if> " +
        "<if test='language != null and language != \"\"'>AND s.language_code=#{language}</if> " +
        "<if test='artist != null and artist != \"\"'>AND s.artist LIKE CONCAT('%',#{artist},'%')</if> " +
        "ORDER BY s.play_count DESC,s.id DESC LIMIT 10</script>")
    List<Map<String,Object>> aiSearch(@Param("keyword") String keyword, @Param("genre") String genre,
        @Param("language") String language, @Param("artist") String artist);
}
