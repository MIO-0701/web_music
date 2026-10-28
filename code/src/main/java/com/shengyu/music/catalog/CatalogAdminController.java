package com.shengyu.music.catalog;

import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/songs")
public class CatalogAdminController {
    private final CatalogAdminService admin;
    private final CatalogService catalog;
    private final BatchSongImportService batches;
    public CatalogAdminController(CatalogAdminService admin, CatalogService catalog, BatchSongImportService batches) { this.admin = admin; this.catalog = catalog; this.batches = batches; }

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
        if (audio.getSize() > 50_000_000L || (cover != null && cover.getSize() > 10_000_000L))
            throw ApiException.badRequest("单曲音频须不超过 50 MB，封面须不超过 10 MB");
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

    public record BatchRequest(List<Long> ids, String action) {}
    @PostMapping("/batch/actions") public Map<String, Object> batch(@RequestBody BatchRequest body) {
        return Map.of("processed", admin.batch(body.ids(), body.action()));
    }
    @GetMapping("/batch/template") public ResponseEntity<byte[]> template() {
        String csv = "\uFEFFtitle,artist,album,audio_file,cover_file,language,genre,vip\r\n示例歌曲,示例歌手,示例专辑,示例歌曲.mp3,示例封面.jpg,cn,pop,false\r\n";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=shengyu-song-template.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @PostMapping(value = "/batch/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadBatch(@RequestPart MultipartFile audioZip, @RequestPart MultipartFile coverZip,
        @RequestPart MultipartFile csv) throws Exception { return batches.upload(audioZip, coverZip, csv); }
}
