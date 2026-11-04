package com.shengyu.music.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 认证接口层：接收注册、登录、退出和当前用户请求，调用认证业务层返回结果。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    /** 注册请求，包含用户名、密码和可选昵称。 */
    public record RegisterRequest(@NotBlank String username, @NotBlank String password, String displayName) {}
    /** 登录请求，包含用户名和密码。 */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /**
     * 接收注册并返回会话请求，调用对应业务服务并返回接口响应。

     * @param request 已校验的请求内容
     * @return 接口响应
     */
    @PostMapping("/register") public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.username(), request.password(), request.displayName());
    }
    /**
     * 接收登录并返回会话请求，调用对应业务服务并返回接口响应。
     * @param request 已校验的请求内容
     * @return 接口响应
     */
    @PostMapping("/login") public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }
    /**
     * 接收注销当前会话请求，调用对应业务服务并返回接口响应。
     * @param header header 参数
     * @return 接口响应
     */
    @PostMapping("/logout") public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        auth.logout(header != null && header.startsWith("Bearer ") ? header.substring(7) : null);
        return Map.of("message", "已退出登录");
    }
    /**
     * 接收获取当前登录用户请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @GetMapping("/me") public AuthUser me() { return AuthService.current(); }
}
