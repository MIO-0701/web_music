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

/** 后台管理业务层：处理用户状态、会员申请、评论审核与统计看板，更新 MySQL 业务表。 */
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

    /**
     * 分页查看用户。
     * 处理流程：按用户名或昵称筛选 users 表，只选择后台列表需要的字段。
     * @param keyword 可选用户名或昵称关键词
     * @param page 从 1 开始的页码
     * @param size 每页记录数
     * @return 用户分页结果
     */
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

    /** 会员到期时间请求；null 表示取消会员。 */
    public record MembershipRequest(LocalDateTime expiresAt) {}
    /**
     * 直接设置用户会员到期时间。
     * 处理流程：校验到期时间，限定普通用户后更新 users.membership_expires_at。
     * @param id 记录 ID
     * @param body 请求内容
     * @return 会员状态更新消息
     */
    public Map<String, String> membership(@PathVariable long id, @RequestBody MembershipRequest body) {
        if (body.expiresAt() != null && body.expiresAt().isBefore(LocalDateTime.now())) throw ApiException.badRequest("到期时间必须晚于现在；取消会员请传 null");
        if (usersMapper.update(null, Wrappers.<Users>update().eq("id", id).eq("role", "USER").set("membership_expires_at", body.expiresAt())) == 0) throw ApiException.notFound("用户不存在");
        return Map.of("message", "会员状态已更新");
    }
    /**
     * 启用或禁用普通用户。
     * 处理流程：限定 USER 角色，更新 users.enabled 并检查是否存在。
     * @param id 记录 ID
     * @param value 待设置的新状态或值
     * @return 用户状态更新消息
     */
    public Map<String, String> enabled(@PathVariable long id, @RequestParam boolean value) {
        if (usersMapper.update(null, Wrappers.<Users>update().eq("id", id).eq("role", "USER").set("enabled", value)) == 0) throw ApiException.notFound("用户不存在");
        return Map.of("message", "用户状态已更新");
    }

    /**
     * 查看待审核会员申请。
     * 处理流程：关联申请和用户信息，返回待审核列表。
     * @return 待审核申请列表
     */
    public List<Map<String, Object>> membershipRequests() {
        return requests.pendingWithUsers();
    }
    /**
     * 审核会员申请。
     * 处理流程：校验申请与审核决定；通过时更新会员到期时间，并记录申请处理状态。
     * @param id 记录 ID
     * @param decision decision 参数
     * @return 审核结果消息
     */
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

    /**
     * 分页查看评论审核列表。
     * 处理流程：按审核状态及歌曲名称筛选 comments，并返回管理端分页结果。
     * @param status 可选审核状态
     * @param song 可选歌曲标题关键词
     * @param page 从 1 开始的页码
     * @param size 每页记录数
     * @return 评论分页结果
     */
    public PageResult<Map<String, Object>> comments(@RequestParam(required = false) String status, @RequestParam(required = false) String song,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        String filter = status == null || status.isBlank() ? null : status.toUpperCase();
        String title = song == null || song.isBlank() ? null : song.trim();
        long total = commentsMapper.adminCount(filter, title);
        var items = commentsMapper.adminList(filter, title, s, (p - 1) * s);
        return new PageResult<>(items, total, p, s);
    }
    /**
     * 审核或隐藏评论。
     * 处理流程：校验目标状态，更新 comments.status 并确认评论存在。
     * @param id 记录 ID
     * @param value 待设置的新状态或值
     * @return 评论状态更新消息
     */
    public Map<String, String> commentStatus(@PathVariable long id, @RequestParam String value) {
        String status = value.toUpperCase();
        if (!List.of("APPROVED", "HIDDEN").contains(status)) throw ApiException.badRequest("不支持的评论状态");
        if (commentsMapper.update(null, Wrappers.<Comments>update().eq("id", id).set("status", status)) == 0) throw ApiException.notFound("评论不存在");
        return Map.of("message", "评论状态已更新");
    }

    /**
     * 汇总后台看板数据。
     * 处理流程：分别统计用户、会员、歌曲、今日播放、待审评论、热门歌曲和最近实验。
     * @return 后台各项统计数据
     */
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
