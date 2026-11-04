package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface SongTagLinksMapper extends BaseMapper<SongTagLinks> {
    @Select("SELECT l.song_id,t.name FROM song_tag_links l JOIN song_tags t ON t.id=l.tag_id JOIN songs s ON s.id=l.song_id WHERE s.published=TRUE")
    List<Map<String,Object>> publishedTags();
    @Insert("INSERT IGNORE INTO song_tag_links(song_id,tag_id) VALUES(#{songId},#{tagId})")
    int insertIgnore(@Param("songId") long songId, @Param("tagId") long tagId);
}
