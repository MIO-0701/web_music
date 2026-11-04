package com.shengyu.music.auth;

import com.shengyu.music.common.ApiException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import com.shengyu.music.persistence.Users;
import com.shengyu.music.persistence.UsersMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private final UsersMapper users;
    private final PasswordEncoder passwords;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UsersMapper users, PasswordEncoder passwords, StringRedisTemplate redis) {
        this.users = users; this.passwords = passwords; this.redis = redis;
    }

    public Map<String, Object> register(String username, String password, String displayName) {
        String name = username == null ? "" : username.trim();
        if (!name.matches("[a-zA-Z0-9_]{3,30}")) throw ApiException.badRequest("用户名须为 3–30 位字母、数字或下划线");
        if (password == null || password.length() < 8 || password.length() > 72) throw ApiException.badRequest("密码长度须为 8–72 位");
        if (users.selectCount(Wrappers.<Users>query().eq("username", name)) > 0) throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        String label = displayName == null || displayName.isBlank() ? name : displayName.trim();
        if (label.length() > 80) throw ApiException.badRequest("昵称过长");
        Users created = new Users(); created.username = name; created.passwordHash = passwords.encode(password); created.displayName = label; created.role = "USER"; users.insert(created);
        return login(name, password);
    }

    public Map<String, Object> login(String username, String password) {
        Users row = users.selectOne(Wrappers.<Users>query().eq("username", username == null ? "" : username.trim()));
        if (row == null || !passwords.matches(password == null ? "" : password, row.passwordHash))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        if (!Boolean.TRUE.equals(row.enabled)) throw ApiException.forbidden("账号已禁用");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set("auth:" + token, String.valueOf(row.id), TOKEN_TTL);
        return Map.of("token", token, "tokenType", "Bearer", "expiresIn", TOKEN_TTL.toSeconds(), "user", toUser(row));
    }

    public void logout(String token) { if (token != null && !token.isBlank()) redis.delete("auth:" + token); }

    public AuthUser fromToken(String token) {
        if (token == null || token.isBlank()) return null;
        String value = redis.opsForValue().get("auth:" + token);
        if (value == null) return null;
        Users user = users.selectById(Long.parseLong(value));
        if (user == null || !Boolean.TRUE.equals(user.enabled)) return null;
        return toUser(user);
    }

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        return user;
    }

    private AuthUser toUser(Users row) {
        return new AuthUser(row.id, row.username, row.displayName, row.role, row.membershipExpiresAt);
    }
}
