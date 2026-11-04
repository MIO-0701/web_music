package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

/** 持久层实体：映射 MySQL shengyu 库的 users：账号、密码哈希、角色、启用状态和会员到期时间。 */
@TableName("users")
public class Users {
    @TableId(value = "id", type = IdType.AUTO)
    public Long id;
    @TableField("username")
    public String username;
    @TableField("password_hash")
    public String passwordHash;
    @TableField("display_name")
    public String displayName;
    @TableField("role")
    public String role;
    @TableField("enabled")
    public Boolean enabled;
    @TableField("membership_expires_at")
    public java.time.LocalDateTime membershipExpiresAt;
    @TableField("created_at")
    public java.time.LocalDateTime createdAt;
}
