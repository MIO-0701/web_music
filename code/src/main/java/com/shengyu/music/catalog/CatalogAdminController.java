package com.shengyu.music.catalog;

import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/songs")
public class CatalogAdminController {
    private final CatalogAdminService admin;
    private final CatalogService catalog;
    public CatalogAdminController(CatalogAdminService admin, CatalogService catalog) { this.admin = admin; this.catalog = catalog; }

    @GetMapping public PageResult<Map<String, Object>> list(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return catalog.search(keyword, null, null, null, page, size, true);
    }

    @GetMapping("/{id}") public Map<String, Object> detail(@PathVariable long id) { return catalog.song(id, true); }

    @PostMapping(consumes = "multipart/form-data")
    public Map<String, Object> upload(@RequestPart MultipartFile audio, @RequestPart(required = false) MultipartFile cover,
        @RequestParam String title, @RequestParam String artist, @RequestParam(defaultValue = "") String album,
        @RequestParam(defaultValue = "other") String language, @RequestParam(defaultValue = "未分类") String genre,
        @RequestParam(defaultValue = "false") boolean vip, @RequestParam(required = false) List<String> tags) throws Exception {
        if (audio.isEmpty()) throw ApiException.badRequest("音频文件不能为空");
        if (audio.getOriginalFilename() == null || !audio.getOriginalFilename().toLowerCase().endsWith(".mp3"))
            throw ApiException.badRequest("仅支持 MP3 音频");
        Path audioTemp = Files.createTempFile("shengyu-upload-", ".mp3");
        Path coverTemp = null;
        try {
            audio.transferTo(audioTemp);
            if (cover != null && !cover.isEmpty()) {
                String name = cover.getOriginalFilename() == null ? "cover.jpg" : cover.getOriginalFilename();
                String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
                if (!List.of("jpg", "jpeg", "png", "webp").contains(extension.toLowerCase())) throw ApiException.badRequest("封面格式不支持");
                coverTemp = Files.createTempFile("shengyu-cover-", "." + extension);
                cover.transferTo(coverTemp);
            }
            long id = admin.add(audioTemp, coverTemp, title, artist, album, language, genre, vip, null, tags);
            return Map.of("id", id);
        } finally { Files.deleteIfExists(audioTemp); if (coverTemp != null) Files.deleteIfExists(coverTemp); }
    }

    public record EditSong(String title, String artist, String album, String genre, String description,
        boolean vip, boolean published, List<String> tags) {}
    @PutMapping("/{id}") public Map<String, String> edit(@PathVariable long id, @RequestBody EditSong body) {
        admin.edit(id, body.title(), body.artist(), body.album(), body.genre(), body.description(), body.vip(), body.published(), body.tags());
        return Map.of("message", "已更新");
    }
    @PatchMapping("/{id}/publish") public Map<String, String> publish(@PathVariable long id, @RequestParam boolean value) {
        admin.publish(id, value); return Map.of("message", value ? "已上架" : "已下架");
    }
}
