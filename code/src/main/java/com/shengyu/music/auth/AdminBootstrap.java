package com.shengyu.music.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    @Bean ApplicationRunner createInitialAdmin(JdbcTemplate jdbc, PasswordEncoder passwords,
        @Value("${music.admin.username:}") String username, @Value("${music.admin.password:}") String password) {
        return args -> {
            if (username.isBlank() || password.isBlank()) { log.warn("ADMIN_USERNAME / ADMIN_PASSWORD not set; no initial administrator will be created"); return; }
            if (password.length() < 12) throw new IllegalArgumentException("ADMIN_PASSWORD must contain at least 12 characters");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'", Long.class) == 0)
                jdbc.update("INSERT INTO users(username,password_hash,display_name,role) VALUES(?,?,?,'ADMIN')", username, passwords.encode(password), "系统管理员");
        };
    }
}
