package com.shengyu.music.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    @Test void readsJdbcLocalDateTimeMembershipWithoutInvalidatingToken() {
        verifyMembership(LocalDateTime.now().plusDays(1));
    }

    @Test void readsTimestampMembershipWithoutInvalidatingToken() {
        verifyMembership(Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
    }

    private void verifyMembership(Object expiry) {
        var jdbc = mock(JdbcTemplate.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("auth:token")).thenReturn("7");
        var row = new HashMap<String, Object>();
        row.put("id", 7L);
        row.put("username", "listener");
        row.put("display_name", "听众");
        row.put("role", "USER");
        row.put("enabled", true);
        row.put("membership_expires_at", expiry);
        when(jdbc.queryForList("SELECT id,username,display_name,role,enabled,membership_expires_at FROM users WHERE id=?", 7L))
            .thenReturn(List.of(row));

        var user = new AuthService(jdbc, mock(PasswordEncoder.class), redis).fromToken("token");
        assertThat(user).isNotNull();
        assertThat(user.member()).isTrue();
    }
}
