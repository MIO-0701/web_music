package com.shengyu.music.auth;

import java.time.LocalDateTime;

/** 认证上下文中的用户快照：保存身份、角色和会员到期时间，供权限与播放判断使用。 */
public record AuthUser(long id, String username, String displayName, String role, LocalDateTime membershipExpiresAt) {
    /**
     * 判断用户会员资格是否仍在有效期内。
     * @return 操作结果
     */
    public boolean member() { return membershipExpiresAt != null && membershipExpiresAt.isAfter(LocalDateTime.now()); }
}
