package com.shengyu.music.catalog;

import com.shengyu.music.common.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PreviewGenerator {
    private final String ffmpeg;
    private final int seconds;
    public PreviewGenerator(@Value("${music.preview.ffmpeg}") String ffmpeg, @Value("${music.preview.seconds}") int seconds) {
        this.ffmpeg = ffmpeg; this.seconds = seconds;
    }
    public Path generate(Path source) {
        try {
            Path target = Files.createTempFile("shengyu-preview-", ".mp3");
            Process process = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-i", source.toString(),
                "-t", String.valueOf(seconds), "-vn", "-codec:a", "libmp3lame", "-q:a", "5", target.toString())
                .redirectErrorStream(true).start();
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            if (!finished || process.exitValue() != 0 || Files.size(target) == 0) {
                Files.deleteIfExists(target);
                throw new IllegalStateException("FFmpeg failed");
            }
            return target;
        } catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "生成试听文件失败，请检查 FFmpeg 安装"); }
    }
}
