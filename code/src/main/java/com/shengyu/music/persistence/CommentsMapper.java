package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

/** 持久层 Mapper：连接 MySQL shengyu 库的 comments：歌曲评论正文、作者和审核状态。 提供该表的数据访问方法。 */
@Mapper
public interface CommentsMapper extends BaseMapper<Comments> {
    @Select("<script>SELECT COUNT(*) FROM comments c JOIN users u ON u.id=c.user_id JOIN songs s ON s.id=c.song_id WHERE 1=1 " +
        "<if test='status != null and status != \"\"'>AND c.status=#{status}</if> " +
        "<if test='song != null and song != \"\"'>AND s.title LIKE CONCAT('%',#{song},'%')</if></script>")
    long adminCount(@Param("status") String status, @Param("song") String song);
    @Select("<script>SELECT c.id,c.content,c.status,c.created_at,u.display_name,s.title song_title " +
        "FROM comments c JOIN users u ON u.id=c.user_id JOIN songs s ON s.id=c.song_id WHERE 1=1 " +
        "<if test='status != null and status != \"\"'>AND c.status=#{status}</if> " +
        "<if test='song != null and song != \"\"'>AND s.title LIKE CONCAT('%',#{song},'%')</if> " +
        "ORDER BY c.id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String,Object>> adminList(@Param("status") String status, @Param("song") String song,
        @Param("limit") int limit, @Param("offset") int offset);
    @Select("<script>SELECT c.id,c.content,c.created_at,u.display_name," +
        "COALESCE(SUM(CASE WHEN r.value=1 THEN 1 ELSE 0 END),0) likes," +
        "COALESCE(SUM(CASE WHEN r.value=-1 THEN 1 ELSE 0 END),0) dislikes," +
        "COALESCE((SELECT mine.value FROM comment_reactions mine WHERE mine.comment_id=c.id AND mine.user_id=#{viewerId}),0) my_reaction " +
        "FROM comments c JOIN users u ON u.id=c.user_id LEFT JOIN comment_reactions r ON r.comment_id=c.id " +
        "WHERE c.song_id=#{songId} AND c.status='APPROVED' GROUP BY c.id,c.content,c.created_at,u.display_name " +
        "ORDER BY <choose><when test='sort == \"time\"'>c.created_at</when><otherwise>likes</otherwise></choose> " +
        "<choose><when test='direction == \"asc\"'>ASC</when><otherwise>DESC</otherwise></choose>,c.id " +
        "<choose><when test='direction == \"asc\"'>ASC</when><otherwise>DESC</otherwise></choose> LIMIT #{limit} OFFSET #{offset}</script>")
    List<Map<String,Object>> listApproved(@Param("songId") long songId, @Param("viewerId") long viewerId,
        @Param("sort") String sort, @Param("direction") String direction, @Param("limit") int limit, @Param("offset") int offset);
}
