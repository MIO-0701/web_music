package com.shengyu.music.admin;

import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.shengyu.music.admin.AdminService.*;
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    public AdminController(AdminService service) { this.adminService = service; }
    @GetMapping("/users") public PageResult<Map<String, Object>> users(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return adminService.users(keyword, page, size); }

    @PutMapping("/users/{id}/membership") public Map<String, String> membership(@PathVariable long id, @RequestBody MembershipRequest body) { return adminService.membership(id, body); }
    @PatchMapping("/users/{id}/enabled") public Map<String, String> enabled(@PathVariable long id, @RequestParam boolean value) { return adminService.enabled(id, value); }

    @GetMapping("/membership-requests") public List<Map<String, Object>> membershipRequests() { return adminService.membershipRequests(); }
    @PatchMapping("/membership-requests/{id}") public Map<String, String> reviewMembership(@PathVariable long id, @RequestParam String decision) { return adminService.reviewMembership(id, decision); }

    @GetMapping("/comments") public PageResult<Map<String, Object>> comments(@RequestParam(required = false) String status, @RequestParam(required = false) String song,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return adminService.comments(status, song, page, size); }
    @PatchMapping("/comments/{id}/status") public Map<String, String> commentStatus(@PathVariable long id, @RequestParam String value) { return adminService.commentStatus(id, value); }

    @GetMapping("/dashboard") public Map<String, Object> dashboard() { return adminService.dashboard(); }
}
