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
import com.shengyu.music.persistence.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;

@Service
public class CommentService {
    private final CommentsMapper comments;
    private final CommentReactionsMapper reactions;
    private final CatalogService catalog;
    public CommentService(CommentsMapper comments, CommentReactionsMapper reactions, CatalogService catalog) { this.comments = comments; this.reactions = reactions; this.catalog = catalog; }
    public record CommentRequest(@NotBlank @Size(max = 1000) String content) {}

    public PageResult<Map<String, Object>> list(@PathVariable long songId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "likes") String sort, @RequestParam(defaultValue = "desc") String direction) {
        catalog.song(songId, false); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        if (!sort.equals("likes") && !sort.equals("time")) throw ApiException.badRequest("不支持的排序方式");
        if (!direction.equals("asc") && !direction.equals("desc")) throw ApiException.badRequest("不支持的排序方向");
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        long viewerId = principal instanceof AuthUser viewer ? viewer.id() : 0L;
        long total = comments.selectCount(Wrappers.<Comments>query().eq("song_id", songId).eq("status", "APPROVED"));
        var items = comments.listApproved(songId, viewerId, sort, direction, s, (p - 1) * s);
        return new PageResult<>(items, total, p, s);
    }
    public Map<String, String> react(@PathVariable long songId, @PathVariable long commentId, @RequestParam int value) {
        if (value != 1 && value != -1 && value != 0) throw ApiException.badRequest("不支持的评价");
        if (comments.selectCount(Wrappers.<Comments>query().eq("id", commentId).eq("song_id", songId).eq("status", "APPROVED")) == 0) throw ApiException.notFound("评论不存在");
        long userId = AuthService.current().id();
        if (value == 0) reactions.delete(Wrappers.<CommentReactions>query().eq("comment_id", commentId).eq("user_id", userId));
        else reactions.upsert(commentId, userId, value);
        return Map.of("message", "评价已更新");
    }
    public Map<String, String> create(@PathVariable long songId, @Valid @RequestBody CommentRequest body) {
        catalog.song(songId, false);
        Comments comment = new Comments(); comment.userId = AuthService.current().id(); comment.songId = songId; comment.content = body.content().trim(); comment.status = "PENDING"; comments.insert(comment);
        return Map.of("message", "评论已提交，等待审核");
    }
}
