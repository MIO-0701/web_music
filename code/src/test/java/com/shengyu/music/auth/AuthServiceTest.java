package com.shengyu.music.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import com.shengyu.music.persistence.Users;
import com.shengyu.music.persistence.UsersMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    @Test void readsJdbcLocalDateTimeMembershipWithoutInvalidatingToken() {
        verifyMembership(LocalDateTime.now().plusDays(1));
    }

    private void verifyMembership(LocalDateTime expiry) {
        var users = mock(UsersMapper.class);
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") var values = (ValueOperations<String, String>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("auth:token")).thenReturn("7");
        var row = new Users(); row.id = 7L; row.username = "listener"; row.displayName = "听众";
        row.role = "USER"; row.enabled = true; row.membershipExpiresAt = expiry;
        when(users.selectById(7L)).thenReturn(row);

        var user = new AuthService(users, mock(PasswordEncoder.class), redis).fromToken("token");
        assertThat(user).isNotNull();
        assertThat(user.member()).isTrue();
    }
}
