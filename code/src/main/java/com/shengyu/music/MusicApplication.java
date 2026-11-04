package com.shengyu.music;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 应用入口：启动 Spring Boot、定时任务和各业务模块。 */
@SpringBootApplication
@EnableScheduling
public class MusicApplication {
    /**
     * 启动应用。
     * @param args args 参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MusicApplication.class, args);
    }
}
