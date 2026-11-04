package com.shengyu.music.persistence;

import com.baomidou.mybatisplus.annotation.*;

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
