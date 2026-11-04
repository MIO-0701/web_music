package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/** 持久层 Mapper：连接 MySQL shengyu 库的 comment_reactions：用户对评论的互动记录。 提供该表的数据访问方法。 */
@Mapper
public interface CommentReactionsMapper extends BaseMapper<CommentReactions> {
    @Insert("INSERT INTO comment_reactions(comment_id,user_id,value) VALUES(#{commentId},#{userId},#{value}) ON DUPLICATE KEY UPDATE value=VALUES(value)")
    int upsert(@Param("commentId") long commentId, @Param("userId") long userId, @Param("value") int value);
}
