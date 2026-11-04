package com.shengyu.music.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.shengyu.music.persistence.Users;
import com.shengyu.music.persistence.UsersMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    @Bean ApplicationRunner createInitialAdmin(UsersMapper users, PasswordEncoder passwords,
        @Value("${music.admin.username:}") String username, @Value("${music.admin.password:}") String password) {
        return args -> {
            if (username.isBlank() || password.isBlank()) { log.warn("ADMIN_USERNAME / ADMIN_PASSWORD not set; no initial administrator will be created"); return; }
            if (password.length() < 12) throw new IllegalArgumentException("ADMIN_PASSWORD must contain at least 12 characters");
            if (users.selectCount(Wrappers.<Users>query().eq("role", "ADMIN")) == 0) {
                Users admin = new Users(); admin.username = username; admin.passwordHash = passwords.encode(password); admin.displayName = "系统管理员"; admin.role = "ADMIN"; users.insert(admin);
            }
        };
    }
}
