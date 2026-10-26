package com.shengyu.music.auth;

import com.shengyu.music.common.ApiException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwords, StringRedisTemplate redis) {
        this.jdbc = jdbc; this.passwords = passwords; this.redis = redis;
    }

    public Map<String, Object> register(String username, String password, String displayName) {
        String name = username == null ? "" : username.trim();
        if (!name.matches("[a-zA-Z0-9_]{3,30}")) throw ApiException.badRequest("用户名须为 3–30 位字母、数字或下划线");
        if (password == null || password.length() < 8 || password.length() > 72) throw ApiException.badRequest("密码长度须为 8–72 位");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username=?", Long.class, name) > 0) throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        String label = displayName == null || displayName.isBlank() ? name : displayName.trim();
        if (label.length() > 80) throw ApiException.badRequest("昵称过长");
        jdbc.update("INSERT INTO users(username,password_hash,display_name,role) VALUES(?,?,?,'USER')", name, passwords.encode(password), label);
        return login(name, password);
    }

    public Map<String, Object> login(String username, String password) {
        var rows = jdbc.queryForList("SELECT id,username,display_name,role,password_hash,enabled,membership_expires_at FROM users WHERE username=?", username == null ? "" : username.trim());
        if (rows.isEmpty() || !passwords.matches(password == null ? "" : password, (String) rows.getFirst().get("password_hash")))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        var row = rows.getFirst();
        if (!Boolean.TRUE.equals(row.get("enabled"))) throw ApiException.forbidden("账号已禁用");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set("auth:" + token, String.valueOf(((Number) row.get("id")).longValue()), TOKEN_TTL);
        return Map.of("token", token, "tokenType", "Bearer", "expiresIn", TOKEN_TTL.toSeconds(), "user", toUser(row));
    }

    public void logout(String token) { if (token != null && !token.isBlank()) redis.delete("auth:" + token); }

    public AuthUser fromToken(String token) {
        if (token == null || token.isBlank()) return null;
        String value = redis.opsForValue().get("auth:" + token);
        if (value == null) return null;
        var rows = jdbc.queryForList("SELECT id,username,display_name,role,enabled,membership_expires_at FROM users WHERE id=?", Long.parseLong(value));
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("enabled"))) return null;
        return toUser(rows.getFirst());
    }

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        return user;
    }

    private AuthUser toUser(Map<String, Object> row) {
        Object expiry = row.get("membership_expires_at");
        LocalDateTime expiresAt = expiry instanceof Timestamp timestamp ? timestamp.toLocalDateTime()
            : expiry instanceof LocalDateTime dateTime ? dateTime : null;
        return new AuthUser(((Number) row.get("id")).longValue(), (String) row.get("username"), (String) row.get("display_name"), (String) row.get("role"), expiresAt);
    }
}
