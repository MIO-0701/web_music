package com.shengyu.music.user;

import com.shengyu.music.auth.AuthService;
import com.shengyu.music.auth.AuthUser;
import com.shengyu.music.catalog.CatalogService;
import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import com.shengyu.music.common.SqlRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/songs/{songId}/comments")
public class CommentController {
    private final SqlRepository jdbc;
    private final CatalogService catalog;
    public CommentController(SqlRepository jdbc, CatalogService catalog) { this.jdbc = jdbc; this.catalog = catalog; }
    public record CommentRequest(@NotBlank @Size(max = 1000) String content) {}

    @GetMapping public PageResult<Map<String, Object>> list(@PathVariable long songId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "likes") String sort, @RequestParam(defaultValue = "desc") String direction) {
        catalog.song(songId, false); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        if (!sort.equals("likes") && !sort.equals("time")) throw ApiException.badRequest("不支持的排序方式");
        if (!direction.equals("asc") && !direction.equals("desc")) throw ApiException.badRequest("不支持的排序方向");
        String order = sort.equals("time") ? "c.created_at" : "likes";
        String dir = direction.equals("desc") ? " DESC" : " ASC";
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        long viewerId = principal instanceof AuthUser viewer ? viewer.id() : 0L;
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE song_id=? AND status='APPROVED'", Long.class, songId);
        var items = jdbc.queryForList("SELECT c.id,c.content,c.created_at,u.display_name," +
            "COALESCE(SUM(CASE WHEN r.value=1 THEN 1 ELSE 0 END),0) likes," +
            "COALESCE(SUM(CASE WHEN r.value=-1 THEN 1 ELSE 0 END),0) dislikes," +
            "COALESCE((SELECT mine.value FROM comment_reactions mine WHERE mine.comment_id=c.id AND mine.user_id=?),0) my_reaction " +
            "FROM comments c JOIN users u ON u.id=c.user_id LEFT JOIN comment_reactions r ON r.comment_id=c.id " +
            "WHERE c.song_id=? AND c.status='APPROVED' GROUP BY c.id,c.content,c.created_at,u.display_name ORDER BY " + order + dir + ",c.id" + dir + " LIMIT ? OFFSET ?", viewerId, songId, s, (p - 1) * s);
        return new PageResult<>(items, total, p, s);
    }
    @PutMapping("/{commentId}/reaction") public Map<String, String> react(@PathVariable long songId, @PathVariable long commentId, @RequestParam int value) {
        if (value != 1 && value != -1 && value != 0) throw ApiException.badRequest("不支持的评价");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE id=? AND song_id=? AND status='APPROVED'", Long.class, commentId, songId) == 0) throw ApiException.notFound("评论不存在");
        long userId = AuthService.current().id();
        if (value == 0) jdbc.update("DELETE FROM comment_reactions WHERE comment_id=? AND user_id=?", commentId, userId);
        else jdbc.update("INSERT INTO comment_reactions(comment_id,user_id,value) VALUES(?,?,?) ON DUPLICATE KEY UPDATE value=VALUES(value)", commentId, userId, value);
        return Map.of("message", "评价已更新");
    }
    @PostMapping public Map<String, String> create(@PathVariable long songId, @Valid @RequestBody CommentRequest body) {
        catalog.song(songId, false);
        jdbc.update("INSERT INTO comments(user_id,song_id,content,status) VALUES(?,?,?,'PENDING')", AuthService.current().id(), songId, body.content().trim());
        return Map.of("message", "评论已提交，等待审核");
    }
}
