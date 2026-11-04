package com.shengyu.music.admin;

import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import com.shengyu.music.persistence.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private final UsersMapper usersMapper;
    private final MembershipRequestsMapper requests;
    private final CommentsMapper commentsMapper;
    private final SongsMapper songsMapper;
    private final PlayEventsMapper plays;
    private final RecommendationExperimentsMapper experiments;
    public AdminService(UsersMapper usersMapper, MembershipRequestsMapper requests, CommentsMapper commentsMapper,
        SongsMapper songsMapper, PlayEventsMapper plays, RecommendationExperimentsMapper experiments) {
        this.usersMapper = usersMapper; this.requests = requests; this.commentsMapper = commentsMapper;
        this.songsMapper = songsMapper; this.plays = plays; this.experiments = experiments;
    }

    public PageResult<Map<String, Object>> users(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        var query = Wrappers.<Users>query();
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like("username", keyword.trim()).or().like("display_name", keyword.trim()));
        long total = usersMapper.selectCount(query);
        query.select("id","username","display_name","role","enabled","membership_expires_at","created_at").orderByDesc("id").last("LIMIT " + s + " OFFSET " + ((p - 1) * s));
        List<Map<String, Object>> items = usersMapper.selectMaps(query);
        return new PageResult<>(items, total, p, s);
    }

    public record MembershipRequest(LocalDateTime expiresAt) {}
    public Map<String, String> membership(@PathVariable long id, @RequestBody MembershipRequest body) {
        if (body.expiresAt() != null && body.expiresAt().isBefore(LocalDateTime.now())) throw ApiException.badRequest("到期时间必须晚于现在；取消会员请传 null");
        if (usersMapper.update(null, Wrappers.<Users>update().eq("id", id).eq("role", "USER").set("membership_expires_at", body.expiresAt())) == 0) throw ApiException.notFound("用户不存在");
        return Map.of("message", "会员状态已更新");
    }
    public Map<String, String> enabled(@PathVariable long id, @RequestParam boolean value) {
        if (usersMapper.update(null, Wrappers.<Users>update().eq("id", id).eq("role", "USER").set("enabled", value)) == 0) throw ApiException.notFound("用户不存在");
        return Map.of("message", "用户状态已更新");
    }

    public List<Map<String, Object>> membershipRequests() {
        return requests.pendingWithUsers();
    }
    @Transactional
    public Map<String, String> reviewMembership(@PathVariable long id, @RequestParam String decision) {
        String status = decision.toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(status)) throw ApiException.badRequest("不支持的审核决定");
        Long userId = requests.pendingUserForUpdate(id);
        if (userId == null) throw ApiException.notFound("待审核申请不存在");
        if (status.equals("APPROVED")) usersMapper.extendMembership(userId);
        requests.update(null, Wrappers.<MembershipRequests>update().eq("id", id).set("status", status)
            .set("reviewed_at", LocalDateTime.now()).set("reviewer_id", com.shengyu.music.auth.AuthService.current().id()));
        return Map.of("message", "申请已审核");
    }

    public PageResult<Map<String, Object>> comments(@RequestParam(required = false) String status, @RequestParam(required = false) String song,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        String filter = status == null || status.isBlank() ? null : status.toUpperCase();
        String title = song == null || song.isBlank() ? null : song.trim();
        long total = commentsMapper.adminCount(filter, title);
        var items = commentsMapper.adminList(filter, title, s, (p - 1) * s);
        return new PageResult<>(items, total, p, s);
    }
    public Map<String, String> commentStatus(@PathVariable long id, @RequestParam String value) {
        String status = value.toUpperCase();
        if (!List.of("APPROVED", "HIDDEN").contains(status)) throw ApiException.badRequest("不支持的评论状态");
        if (commentsMapper.update(null, Wrappers.<Comments>update().eq("id", id).set("status", status)) == 0) throw ApiException.notFound("评论不存在");
        return Map.of("message", "评论状态已更新");
    }

    public Map<String, Object> dashboard() {
        return Map.of(
            "users", usersMapper.selectCount(Wrappers.<Users>query().eq("role", "USER")),
            "members", usersMapper.selectCount(Wrappers.<Users>query().gt("membership_expires_at", LocalDateTime.now())),
            "songs", songsMapper.selectCount(Wrappers.<Songs>query().eq("published", true)),
            "playsToday", plays.selectCount(Wrappers.<PlayEvents>query().ge("created_at", java.time.LocalDate.now().atStartOfDay())),
            "pendingComments", commentsMapper.selectCount(Wrappers.<Comments>query().eq("status", "PENDING")),
            "topSongs", songsMapper.selectMaps(Wrappers.<Songs>query().select("id","title","artist","play_count").eq("published", true).orderByDesc("play_count","id").last("LIMIT 5")),
            "dailyPlays", plays.dailyPlays(),
            "genres", songsMapper.topGenres(),
            "experiments", experiments.selectMaps(Wrappers.<RecommendationExperiments>query().select("algorithm","dataset","precision_at_10","recall_at_10","coverage","evaluated_users","created_at").orderByDesc("id").last("LIMIT 4"))
        );
    }
}
