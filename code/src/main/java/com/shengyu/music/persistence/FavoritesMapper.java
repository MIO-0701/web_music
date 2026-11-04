package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/** 持久层 Mapper：连接 MySQL shengyu 库的 favorites：用户收藏歌曲的关联关系。 提供该表的数据访问方法。 */
@Mapper
public interface FavoritesMapper extends BaseMapper<Favorites> {
    @Insert("INSERT IGNORE INTO favorites(user_id,song_id) VALUES(#{userId},#{songId})")
    int insertIgnore(@Param("userId") long userId, @Param("songId") long songId);
    @Select("<script>SELECT COUNT(*) FROM favorites f JOIN songs s ON s.id=f.song_id WHERE f.user_id=#{userId} AND s.published=TRUE " +
        "<if test='keyword != null and keyword != \"\"'>AND (s.title LIKE CONCAT('%',#{keyword},'%') OR s.artist LIKE CONCAT('%',#{keyword},'%') OR s.album_name LIKE CONCAT('%',#{keyword},'%'))</if></script>")
    long countVisible(@Param("userId") long userId, @Param("keyword") String keyword);
    @Select("<script>SELECT s.id,s.title,s.artist,s.album_name,s.genre,s.duration_seconds,s.vip,s.play_count,f.created_at " +
        "FROM favorites f JOIN songs s ON s.id=f.song_id WHERE f.user_id=#{userId} AND s.published=TRUE " +
        "<if test='keyword != null and keyword != \"\"'>AND (s.title LIKE CONCAT('%',#{keyword},'%') OR s.artist LIKE CONCAT('%',#{keyword},'%') OR s.album_name LIKE CONCAT('%',#{keyword},'%'))</if> " +
        "ORDER BY f.created_at DESC,s.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String,Object>> listVisible(@Param("userId") long userId, @Param("keyword") String keyword,
        @Param("limit") int limit, @Param("offset") int offset);
}
