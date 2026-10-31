package com.shengyu.music.admin;

import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate jdbc;
    public AdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping("/users") public PageResult<Map<String, Object>> users(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        String where = keyword == null || keyword.isBlank() ? "" : " WHERE username LIKE ? OR display_name LIKE ?";
        String term = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        Object[] filter = where.isBlank() ? new Object[0] : new Object[]{term, term};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM users" + where, Long.class, filter);
        List<Map<String, Object>> items = where.isBlank()
            ? jdbc.queryForList("SELECT id,username,display_name,role,enabled,membership_expires_at,created_at FROM users ORDER BY id DESC LIMIT ? OFFSET ?", s, (p - 1) * s)
            : jdbc.queryForList("SELECT id,username,display_name,role,enabled,membership_expires_at,created_at FROM users" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", term, term, s, (p - 1) * s);
        return new PageResult<>(items, total, p, s);
    }

    public record MembershipRequest(LocalDateTime expiresAt) {}
    @PutMapping("/users/{id}/membership") public Map<String, String> membership(@PathVariable long id, @RequestBody MembershipRequest body) {
        if (body.expiresAt() != null && body.expiresAt().isBefore(LocalDateTime.now())) throw ApiException.badRequest("到期时间必须晚于现在；取消会员请传 null");
        if (jdbc.update("UPDATE users SET membership_expires_at=? WHERE id=? AND role='USER'", body.expiresAt(), id) == 0) throw ApiException.notFound("用户不存在");
        return Map.of("message", "会员状态已更新");
    }
    @PatchMapping("/users/{id}/enabled") public Map<String, String> enabled(@PathVariable long id, @RequestParam boolean value) {
        if (jdbc.update("UPDATE users SET enabled=? WHERE id=? AND role='USER'", value, id) == 0) throw ApiException.notFound("用户不存在");
        return Map.of("message", "用户状态已更新");
    }

    @GetMapping("/membership-requests") public List<Map<String, Object>> membershipRequests() {
        return jdbc.queryForList("SELECT r.id,r.user_id,r.status,r.created_at,u.display_name,u.username FROM membership_requests r JOIN users u ON u.id=r.user_id WHERE r.status='PENDING' ORDER BY r.id ASC");
    }
    @Transactional
    @PatchMapping("/membership-requests/{id}") public Map<String, String> reviewMembership(@PathVariable long id, @RequestParam String decision) {
        String status = decision.toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(status)) throw ApiException.badRequest("不支持的审核决定");
        var rows = jdbc.queryForList("SELECT user_id FROM membership_requests WHERE id=? AND status='PENDING' FOR UPDATE", id);
        if (rows.isEmpty()) throw ApiException.notFound("待审核申请不存在");
        long userId = ((Number) rows.getFirst().get("user_id")).longValue();
        if (status.equals("APPROVED")) jdbc.update("UPDATE users SET membership_expires_at=DATE_ADD(GREATEST(COALESCE(membership_expires_at,NOW()),NOW()),INTERVAL 30 DAY) WHERE id=? AND role='USER'", userId);
        jdbc.update("UPDATE membership_requests SET status=?,reviewed_at=NOW(),reviewer_id=? WHERE id=?", status, com.shengyu.music.auth.AuthService.current().id(), id);
        return Map.of("message", "申请已审核");
    }

    @GetMapping("/comments") public PageResult<Map<String, Object>> comments(@RequestParam(required = false) String status, @RequestParam(required = false) String song,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        var args = new ArrayList<Object>();
        String condition = " WHERE 1=1";
        if (status != null && !status.isBlank()) { condition += " AND c.status=?"; args.add(status.toUpperCase()); }
        if (song != null && !song.isBlank()) { condition += " AND s.title LIKE ?"; args.add("%" + song.trim() + "%"); }
        String from = " FROM comments c JOIN users u ON u.id=c.user_id JOIN songs s ON s.id=c.song_id";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + from + condition, Long.class, args.toArray());
        args.add(s); args.add((p - 1) * s);
        var items = jdbc.queryForList("SELECT c.id,c.content,c.status,c.created_at,u.display_name,s.title song_title" + from + condition + " ORDER BY c.id DESC LIMIT ? OFFSET ?", args.toArray());
        return new PageResult<>(items, total, p, s);
    }
    @PatchMapping("/comments/{id}/status") public Map<String, String> commentStatus(@PathVariable long id, @RequestParam String value) {
        String status = value.toUpperCase();
        if (!List.of("APPROVED", "HIDDEN").contains(status)) throw ApiException.badRequest("不支持的评论状态");
        if (jdbc.update("UPDATE comments SET status=? WHERE id=?", status, id) == 0) throw ApiException.notFound("评论不存在");
        return Map.of("message", "评论状态已更新");
    }

    @GetMapping("/dashboard") public Map<String, Object> dashboard() {
        return Map.of(
            "users", jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='USER'", Long.class),
            "members", jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE membership_expires_at>NOW()", Long.class),
            "songs", jdbc.queryForObject("SELECT COUNT(*) FROM songs WHERE published=TRUE", Long.class),
            "playsToday", jdbc.queryForObject("SELECT COUNT(*) FROM play_events WHERE created_at>=CURDATE()", Long.class),
            "pendingComments", jdbc.queryForObject("SELECT COUNT(*) FROM comments WHERE status='PENDING'", Long.class),
            "topSongs", jdbc.queryForList("SELECT id,title,artist,play_count FROM songs WHERE published=TRUE ORDER BY play_count DESC,id DESC LIMIT 5"),
            "dailyPlays", jdbc.queryForList("SELECT DATE(created_at) day,COUNT(*) plays FROM play_events WHERE created_at>=DATE_SUB(CURDATE(),INTERVAL 6 DAY) GROUP BY DATE(created_at) ORDER BY day"),
            "genres", jdbc.queryForList("SELECT genre,COUNT(*) count FROM songs WHERE published=TRUE GROUP BY genre ORDER BY count DESC LIMIT 8"),
            "experiments", jdbc.queryForList("SELECT algorithm,dataset,precision_at_10,recall_at_10,coverage,evaluated_users,created_at FROM recommendation_experiments ORDER BY id DESC LIMIT 4")
        );
    }
}
