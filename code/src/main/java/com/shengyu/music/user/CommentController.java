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
@RestController
@RequestMapping("/api/songs/{songId}/comments")
public class CommentController {
    private final CommentService commentService;
    public CommentController(CommentService service) { this.commentService = service; }
    @GetMapping public PageResult<Map<String, Object>> list(@PathVariable long songId,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "likes") String sort, @RequestParam(defaultValue = "desc") String direction) { return commentService.list(songId, page, size, sort, direction); }
    @PutMapping("/{commentId}/reaction") public Map<String, String> react(@PathVariable long songId, @PathVariable long commentId, @RequestParam int value) { return commentService.react(songId, commentId, value); }
    @PostMapping public Map<String, String> create(@PathVariable long songId, @Valid @RequestBody CommentRequest body) { return commentService.create(songId, body); }
}
