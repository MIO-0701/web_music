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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.shengyu.music.user.CommentService.*;
/** 评论接口层：提供评论查看、提交和互动请求，调用评论业务层。 */
@RestController
@RequestMapping("/api/songs/{songId}/comments")
public class CommentController {
    private final CommentService commentService;
    public CommentController(CommentService service) { this.commentService = service; }
    @GetMapping public PageResult<Map<String, Object>> list(@PathVariable long songId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "likes") String sort, @RequestParam(defaultValue = "desc") String direction) { return commentService.list(songId, page, size, sort, direction); }
    /**
     * 接收设置评论互动请求，调用对应业务服务并返回接口响应。
     * @param songId 歌曲 ID
     * @param commentId commentId 参数
     * @param value 待设置的新状态或值
     * @return 接口响应
     */
    @PutMapping("/{commentId}/reaction") public Map<String, String> react(@PathVariable long songId, @PathVariable long commentId, @RequestParam int value) { return commentService.react(songId, commentId, value); }
    /**
     * 接收提交评论请求，调用对应业务服务并返回接口响应。
     * @param songId 歌曲 ID
     * @param body 已解析的请求内容
     * @return 接口响应
     */
    @PostMapping public Map<String, String> create(@PathVariable long songId, @Valid @RequestBody CommentRequest body) { return commentService.create(songId, body); }
}
