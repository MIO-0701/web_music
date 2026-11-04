package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface RecommendationsMapper extends BaseMapper<Recommendations> {
    @Select("SELECT s.id,s.title,s.artist,s.album_name,s.genre,s.duration_seconds,s.vip,r.score,r.reason,r.generated_at FROM recommendations r JOIN songs s ON s.id=r.song_id WHERE r.user_id=#{userId} AND r.algorithm=#{algorithm} AND s.published=TRUE ORDER BY r.score DESC,s.id LIMIT 20")
    List<Map<String,Object>> forUser(@Param("userId") long userId, @Param("algorithm") String algorithm);
}
