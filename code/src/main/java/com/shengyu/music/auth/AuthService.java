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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 认证业务层：校验注册与登录信息；用户和密码哈希写入 MySQL users 表，随机登录令牌写入 Redis。 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private final UsersMapper users;
    private final PasswordEncoder passwords;
    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UsersMapper users, PasswordEncoder passwords, StringRedisTemplate redis) {
        this.users = users; this.passwords = passwords; this.redis = redis;
    }

    /**
     * 注册用户并直接创建登录会话。
     * 处理流程：校验用户名和密码，检查重名，使用 BCrypt 编码密码后写入 users 表，再调用登录逻辑生成令牌。
     * @param username 用户名
     * @param password 登录密码
     * @param displayName 显示昵称
     * @return 包含令牌、有效期和用户信息的响应
     */
    public Map<String, Object> register(String username, String password, String displayName) {
        String name = username == null ? "" : username.trim();
        if (!name.matches("[a-zA-Z0-9_]{3,30}")) throw ApiException.badRequest("用户名须为 3–30 位字母、数字或下划线");
        if (password == null || password.length() < 8 || password.length() > 72) throw ApiException.badRequest("密码长度须为 8–72 位");
        if (users.selectCount(Wrappers.<Users>query().eq("username", name)) > 0) throw new ApiException(HttpStatus.CONFLICT, "用户名已存在");
        String label = displayName == null || displayName.isBlank() ? name : displayName.trim();
        if (label.length() > 80) throw ApiException.badRequest("昵称过长");
        Users created = new Users(); created.username = name; created.passwordHash = passwords.encode(password); created.displayName = label; created.role = "USER"; users.insert(created);
        log.info("用户注册成功，userId={}", created.id);
        return login(name, password);
    }

    /**
     * 验证用户并创建七天有效的登录会话。
     * 处理流程：从 users 表查询账号，校验密码及启用状态；生成随机令牌，写入 Redis 并设置过期时间。
     * @param username 用户名
     * @param password 登录密码
     * @return 包含 Bearer 令牌、有效期和用户信息的响应
     */
    public Map<String, Object> login(String username, String password) {
        Users row = users.selectOne(Wrappers.<Users>query().eq("username", username == null ? "" : username.trim()));
        if (row == null || !passwords.matches(password == null ? "" : password, row.passwordHash)) {
            log.warn("登录失败：用户名或密码错误");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(row.enabled)) throw ApiException.forbidden("账号已禁用");
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set("auth:" + token, String.valueOf(row.id), TOKEN_TTL);
        log.info("用户登录成功，userId={}", row.id);
        return Map.of("token", token, "tokenType", "Bearer", "expiresIn", TOKEN_TTL.toSeconds(), "user", toUser(row));
    }

    /**
     * 删除登录会话。
     * @param token 登录令牌
     */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete("auth:" + token);
            log.info("用户退出登录");
        }
    }

    /**
     * 解析登录令牌。
     * 处理流程：从 Redis 查询用户 ID，再从 users 表读取当前启用状态与会员期限。
     * @param token 登录令牌
     * @return 有效的认证用户；令牌无效时为 null
     */
    public AuthUser fromToken(String token) {
        if (token == null || token.isBlank()) return null;
        String value = redis.opsForValue().get("auth:" + token);
        if (value == null) return null;
        Users user = users.selectById(Long.parseLong(value));
        if (user == null || !Boolean.TRUE.equals(user.enabled)) return null;
        return toUser(user);
    }

    /**
     * 获取当前已认证用户。
     * @return 操作结果
     */
    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        return user;
    }

    /**
     * 转换为认证用户信息。
     * @param row row 参数
     * @return 操作结果
     */
    private AuthUser toUser(Users row) {
        return new AuthUser(row.id, row.username, row.displayName, row.role, row.membershipExpiresAt);
    }
}
