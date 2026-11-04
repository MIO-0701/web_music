package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/** 持久层 Mapper：连接 MySQL shengyu 库的 users：账号、密码哈希、角色、启用状态和会员到期时间。 提供该表的数据访问方法。 */
@Mapper
public interface UsersMapper extends BaseMapper<Users> {
    @Update("UPDATE users SET membership_expires_at=DATE_ADD(GREATEST(COALESCE(membership_expires_at,NOW()),NOW()),INTERVAL 30 DAY) WHERE id=#{id} AND role='USER'")
    int extendMembership(long id);
}
