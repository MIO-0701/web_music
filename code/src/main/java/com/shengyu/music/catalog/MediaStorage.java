package com.shengyu.music.catalog;

import com.shengyu.music.common.ApiException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MediaStorage {
    private final MinioClient client;
    private final String bucket;

    public MediaStorage(@Value("${music.storage.endpoint}") String endpoint,
        @Value("${music.storage.access-key}") String accessKey,
        @Value("${music.storage.secret-key}") String secretKey,
        @Value("${music.storage.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    @PostConstruct public void ensureBucket() throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 15; attempt++) {
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                return;
            } catch (Exception ex) {
                last = ex;
                Thread.sleep(2000);
            }
        }
        throw last;
    }

    public String put(Path path, String type, String extension) {
        String key = type + "/" + UUID.randomUUID() + "." + extension.toLowerCase();
        try (InputStream input = Files.newInputStream(path)) {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(input, Files.size(path), -1)
                .contentType(contentType(extension)).build());
            return key;
        } catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "文件存储失败"); }
    }

    public long size(String key) {
        try { return client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()).size(); }
        catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "文件读取失败"); }
    }

    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "文件删除失败"); }
    }

    public InputStream read(String key, long offset, long length) {
        try {
            var args = GetObjectArgs.builder().bucket(bucket).object(key).offset(offset);
            if (length >= 0) args.length(length);
            return client.getObject(args.build());
        } catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "文件读取失败"); }
    }

    public static String contentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "mp3" -> "audio/mpeg";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
