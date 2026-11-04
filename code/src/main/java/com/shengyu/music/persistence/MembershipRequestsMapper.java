package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface MembershipRequestsMapper extends BaseMapper<MembershipRequests> {
    @Select("SELECT id,status,created_at,reviewed_at FROM membership_requests WHERE user_id=#{userId} ORDER BY id DESC LIMIT 1")
    Map<String,Object> latestForUser(long userId);
    @Select("SELECT r.id,r.user_id,r.status,r.created_at,u.display_name,u.username FROM membership_requests r JOIN users u ON u.id=r.user_id WHERE r.status='PENDING' ORDER BY r.id ASC")
    List<Map<String,Object>> pendingWithUsers();
    @Select("SELECT user_id FROM membership_requests WHERE id=#{id} AND status='PENDING' FOR UPDATE")
    Long pendingUserForUpdate(long id);
}
