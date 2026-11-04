package com.shengyu.music.catalog;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/** 批量上传配置：单独设置 multipart 请求限制，允许管理员上传歌曲压缩包。 */
@Configuration
public class BatchUploadConfig {
    @Bean
    /**
     * 配置上传文件大小限制。
     * @return 操作结果
     */
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(400));
        factory.setMaxRequestSize(DataSize.ofMegabytes(810));
        return factory.createMultipartConfig();
    }
}
