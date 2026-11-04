package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UsersMapper extends BaseMapper<Users> {
    @Update("UPDATE users SET membership_expires_at=DATE_ADD(GREATEST(COALESCE(membership_expires_at,NOW()),NOW()),INTERVAL 30 DAY) WHERE id=#{id} AND role='USER'")
    int extendMembership(long id);
}
