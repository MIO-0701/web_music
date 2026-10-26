package com.shengyu.music.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    public record RegisterRequest(@NotBlank String username, @NotBlank String password, String displayName) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    @PostMapping("/register") public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.username(), request.password(), request.displayName());
    }
    @PostMapping("/login") public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }
    @PostMapping("/logout") public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        auth.logout(header != null && header.startsWith("Bearer ") ? header.substring(7) : null);
        return Map.of("message", "已退出登录");
    }
    @GetMapping("/me") public AuthUser me() { return AuthService.current(); }
}
