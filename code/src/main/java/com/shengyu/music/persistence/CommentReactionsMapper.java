package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentReactionsMapper extends BaseMapper<CommentReactions> {
    @Insert("INSERT INTO comment_reactions(comment_id,user_id,value) VALUES(#{commentId},#{userId},#{value}) ON DUPLICATE KEY UPDATE value=VALUES(value)")
    int upsert(@Param("commentId") long commentId, @Param("userId") long userId, @Param("value") int value);
}
