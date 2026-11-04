package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/** 持久层 Mapper：连接 MySQL shengyu 库的 play_events：用户的实际播放时长和完成状态，供历史与推荐使用。 提供该表的数据访问方法。 */
@Mapper
public interface PlayEventsMapper extends BaseMapper<PlayEvents> {
    @Select("SELECT song_id,SUM(weight) weight FROM (SELECT song_id,LEAST(COUNT(*),5)*1.0 weight FROM play_events WHERE user_id=#{userId} AND seconds_played>=10 AND created_at>=CURDATE()-INTERVAL 1 DAY AND created_at<CURDATE() GROUP BY song_id UNION ALL SELECT song_id,3.0 weight FROM favorites WHERE user_id=#{userId} AND created_at>=CURDATE()-INTERVAL 1 DAY AND created_at<CURDATE()) x GROUP BY song_id")
    List<Map<String,Object>> yesterdaySignals(long userId);
    @Select("SELECT user_id,song_id,SUM(weight) weight FROM (SELECT user_id,song_id,LEAST(COUNT(*),5) * 1.0 weight FROM play_events WHERE seconds_played>=10 GROUP BY user_id,song_id UNION ALL SELECT user_id,song_id,3.0 weight FROM favorites) x GROUP BY user_id,song_id")
    List<Map<String,Object>> interactions();
    @Select("<script>SELECT COUNT(DISTINCT e.song_id) FROM play_events e JOIN songs s ON s.id=e.song_id WHERE e.user_id=#{userId} AND s.published=TRUE " +
        "<if test='keyword != null and keyword != \"\"'>AND (LOWER(s.title) LIKE CONCAT('%',#{keyword},'%') OR LOWER(s.artist) LIKE CONCAT('%',#{keyword},'%') OR LOWER(s.album_name) LIKE CONCAT('%',#{keyword},'%'))</if></script>")
    long historyCount(@Param("userId") long userId, @Param("keyword") String keyword);
    @Select("<script>SELECT e.id event_id,e.seconds_played,e.completed,e.created_at,s.id song_id,s.title,s.artist,s.album_name,s.genre,s.duration_seconds,s.vip " +
        "FROM (SELECT song_id,MAX(id) latest_id FROM play_events WHERE user_id=#{userId} GROUP BY song_id) latest " +
        "JOIN play_events e ON e.id=latest.latest_id JOIN songs s ON s.id=e.song_id AND s.published=TRUE WHERE 1=1 " +
        "<if test='keyword != null and keyword != \"\"'>AND (LOWER(s.title) LIKE CONCAT('%',#{keyword},'%') OR LOWER(s.artist) LIKE CONCAT('%',#{keyword},'%') OR LOWER(s.album_name) LIKE CONCAT('%',#{keyword},'%'))</if> " +
        "ORDER BY e.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String,Object>> history(@Param("userId") long userId, @Param("keyword") String keyword,
        @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT DATE(created_at) day,COUNT(*) plays FROM play_events WHERE created_at>=DATE_SUB(CURDATE(),INTERVAL 6 DAY) GROUP BY DATE(created_at) ORDER BY day")
    List<Map<String,Object>> dailyPlays();
}
