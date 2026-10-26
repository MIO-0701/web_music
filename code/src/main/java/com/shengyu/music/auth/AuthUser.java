package com.shengyu.music.auth;

import java.time.LocalDateTime;

public record AuthUser(long id, String username, String displayName, String role, LocalDateTime membershipExpiresAt) {
    public boolean member() { return membershipExpiresAt != null && membershipExpiresAt.isAfter(LocalDateTime.now()); }
}
