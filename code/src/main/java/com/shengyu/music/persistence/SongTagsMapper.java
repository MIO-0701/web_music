package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SongTagsMapper extends BaseMapper<SongTags> {
    @Insert("INSERT IGNORE INTO song_tags(name) VALUES(#{name})")
    int insertIgnore(@Param("name") String name);
    @Select("SELECT t.name FROM song_tags t JOIN song_tag_links l ON l.tag_id=t.id WHERE l.song_id=#{songId} ORDER BY t.name")
    List<String> namesForSong(@Param("songId") long songId);
}
