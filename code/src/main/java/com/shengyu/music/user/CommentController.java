package com.shengyu.music.user;

import com.shengyu.music.auth.AuthService;
import com.shengyu.music.catalog.CatalogService;
import com.shengyu.music.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/songs/{songId}/comments")
public class CommentController {
    private final JdbcTemplate jdbc;
    private final CatalogService catalog;
    public CommentController(JdbcTemplate jdbc, CatalogService catalog) { this.jdbc = jdbc; this.catalog = catalog; }
    public record CommentRequest(@NotBlank @Size(max = 1000) String content) {}

    @GetMapping public PageResult<Map<String, Object>> list(@PathVariable long songId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        catalog.song(songId, false); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE song_id=? AND status='APPROVED'", Long.class, songId);
        var items = jdbc.queryForList("SELECT c.id,c.content,c.created_at,u.display_name FROM comments c JOIN users u ON u.id=c.user_id WHERE c.song_id=? AND c.status='APPROVED' ORDER BY c.id DESC LIMIT ? OFFSET ?", songId, s, (p - 1) * s);
        return new PageResult<>(items, total, p, s);
    }
    @PostMapping public Map<String, String> create(@PathVariable long songId, @Valid @RequestBody CommentRequest body) {
        catalog.song(songId, false);
        jdbc.update("INSERT INTO comments(user_id,song_id,content,status) VALUES(?,?,?,'PENDING')", AuthService.current().id(), songId, body.content().trim());
        return Map.of("message", "评论已提交，等待审核");
    }
}
