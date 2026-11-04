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
/** 后台管理接口层：向管理员提供用户、会员、评论和看板接口。 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    public AdminController(AdminService service) { this.adminService = service; }
    /**
     * 接收分页查询用户请求，调用对应业务服务并返回接口响应。
     * @param keyword 可选搜索关键词
     * @param page 从 1 开始的页码
     * @param size 每页记录数
     * @return 接口响应
     */
    @GetMapping("/users") public PageResult<Map<String, Object>> users(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return adminService.users(keyword, page, size); }

    /**
     * 接收设置会员到期时间请求，调用对应业务服务并返回接口响应。

     * @param id 目标记录 ID

     * @param body 已解析的请求内容
     * @return 接口响应
     */
    @PutMapping("/users/{id}/membership") public Map<String, String> membership(@PathVariable long id, @RequestBody MembershipRequest body) { return adminService.membership(id, body); }
    /**
     * 接收修改用户启用状态请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @param value 待设置的新状态或值
     * @return 接口响应
     */
    @PatchMapping("/users/{id}/enabled") public Map<String, String> enabled(@PathVariable long id, @RequestParam boolean value) { return adminService.enabled(id, value); }

    /**
     * 接收查询待审核会员申请请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @GetMapping("/membership-requests") public List<Map<String, Object>> membershipRequests() { return adminService.membershipRequests(); }
    /**
     * 接收审核会员申请请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @param decision decision 参数
     * @return 接口响应
     */
    @PatchMapping("/membership-requests/{id}") public Map<String, String> reviewMembership(@PathVariable long id, @RequestParam String decision) { return adminService.reviewMembership(id, decision); }

    /**
     * 接收分页查询评论请求，调用对应业务服务并返回接口响应。

     * @param status 状态筛选条件

     * @param song song 参数

     * @param page 从 1 开始的页码

     * @param size 每页记录数
     * @return 接口响应
     */
    @GetMapping("/comments") public PageResult<Map<String, Object>> comments(@RequestParam(required = false) String status, @RequestParam(required = false) String song,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return adminService.comments(status, song, page, size); }
    /**
     * 接收修改评论审核状态请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @param value 待设置的新状态或值
     * @return 接口响应
     */
    @PatchMapping("/comments/{id}/status") public Map<String, String> commentStatus(@PathVariable long id, @RequestParam String value) { return adminService.commentStatus(id, value); }

    /**
     * 接收查询后台统计看板请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @GetMapping("/dashboard") public Map<String, Object> dashboard() { return adminService.dashboard(); }
}
