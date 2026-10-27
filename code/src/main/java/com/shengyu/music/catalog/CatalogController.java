package com.shengyu.music.catalog;

import com.shengyu.music.auth.AuthService;
import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/songs")
public class CatalogController {
    private final CatalogService catalog;
    private final MediaStorage storage;
    public CatalogController(CatalogService catalog, MediaStorage storage) { this.catalog = catalog; this.storage = storage; }

    @GetMapping public PageResult<Map<String, Object>> search(@RequestParam(required = false) String keyword,
        @RequestParam(required = false) String genre, @RequestParam(required = false) String language,
        @RequestParam(required = false) Boolean vip,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return catalog.search(keyword, genre, language, vip, page, size, false);
    }
    @GetMapping("/genres") public List<Map<String, Object>> genres() { return catalog.genres(); }
    @GetMapping("/hot") public List<Map<String, Object>> hot() { return catalog.hot(); }
    @GetMapping("/{id}") public Map<String, Object> song(@PathVariable long id) { return catalog.song(id, false); }

    @GetMapping("/{id}/cover") public ResponseEntity<InputStreamResource> cover(@PathVariable long id) {
        catalog.song(id, false);
        String key = (String) catalog.raw(id).get("cover_key");
        if (key == null) throw ApiException.notFound("歌曲没有封面");
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
            .header(HttpHeaders.CONTENT_TYPE, MediaStorage.contentType(key.substring(key.lastIndexOf('.') + 1)))
            .contentLength(storage.size(key)).body(new InputStreamResource(storage.read(key, 0, -1)));
    }

    @GetMapping("/{id}/audio") public ResponseEntity<InputStreamResource> audio(@PathVariable long id,
        @RequestHeader(value = "Range", required = false) String range) {
        var user = AuthService.current();
        var row = catalog.raw(id);
        if (!Boolean.TRUE.equals(row.get("published"))) throw ApiException.notFound("歌曲已下架");
        boolean preview = Boolean.TRUE.equals(row.get("vip")) && !user.member() && !"ADMIN".equals(user.role());
        String key = (String) row.get(preview ? "preview_key" : "audio_key");
        if (key == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "试听文件尚未生成");
        long total = storage.size(key);
        long start = 0, end = total - 1;
        boolean partial = range != null && !range.isBlank();
        if (partial) {
            if (!range.matches("bytes=\\d*-\\d*")) throw new ApiException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "只支持单段字节范围");
            String[] parts = range.substring(6).split("-", -1);
            if (!parts[0].isBlank()) start = Long.parseLong(parts[0]);
            if (!parts[1].isBlank()) end = Long.parseLong(parts[1]);
            if (parts[0].isBlank() && !parts[1].isBlank()) { long suffix = Long.parseLong(parts[1]); start = Math.max(0, total - suffix); end = total - 1; }
            if (start < 0 || start >= total || end < start) throw new ApiException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "无效的字节范围");
            end = Math.min(end, total - 1);
        }
        long length = end - start + 1;
        InputStream stream = storage.read(key, start, length);
        var response = ResponseEntity.status(partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
            .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
            .contentLength(length);
        if (partial) response.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total);
        return response.body(new InputStreamResource(stream));
    }
}
