package com.shengyu.music.user;

import com.shengyu.music.auth.AuthService;
import com.shengyu.music.catalog.CatalogService;
import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import com.shengyu.music.user.UserMusicService.*;
/** 用户音乐接口层：接收收藏、歌单、会员和播放历史请求，并调用用户音乐业务层。 */
@RestController
@RequestMapping("/api/me")
public class UserMusicController {
    private final UserMusicService userMusicService;
    public UserMusicController(UserMusicService service) { this.userMusicService = service; }
    /**
     * 接收分页查询我的收藏请求，调用对应业务服务并返回接口响应。
     * @param keyword 可选搜索关键词
     * @param page 从 1 开始的页码
     * @param size 每页记录数
     * @return 接口响应
     */
    @GetMapping("/favorites") public PageResult<Map<String, Object>> favorites(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return userMusicService.favorites(keyword, page, size); }

    /**
     * 接收收藏歌曲请求，调用对应业务服务并返回接口响应。

     * @param songId 歌曲 ID
     * @return 接口响应
     */
    @PostMapping("/favorites/{songId}") public Map<String, String> favorite(@PathVariable long songId) { return userMusicService.favorite(songId); }
    /**
     * 接收取消歌曲收藏请求，调用对应业务服务并返回接口响应。
     * @param songId 歌曲 ID
     * @return 接口响应
     */
    @DeleteMapping("/favorites/{songId}") public Map<String, String> unfavorite(@PathVariable long songId) { return userMusicService.unfavorite(songId); }

    /**
     * 接收查询我的歌单请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @GetMapping("/playlists") public List<Map<String, Object>> playlists() { return userMusicService.playlists(); }
    /**
     * 接收创建歌单请求，调用对应业务服务并返回接口响应。
     * @param body 已解析的请求内容
     * @return 接口响应
     */
    @PostMapping("/playlists") public Map<String, Object> createPlaylist(@Valid @RequestBody PlaylistRequest body) { return userMusicService.createPlaylist(body); }
    /**
     * 接收查询歌单详情请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @return 接口响应
     */
    @GetMapping("/playlists/{id}") public Map<String, Object> playlist(@PathVariable long id) { return userMusicService.playlist(id); }
    /**
     * 接收修改歌单请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @param body 已解析的请求内容
     * @return 接口响应
     */
    @PutMapping("/playlists/{id}") public Map<String, String> renamePlaylist(@PathVariable long id, @Valid @RequestBody PlaylistRequest body) { return userMusicService.renamePlaylist(id, body); }
    /**
     * 接收删除歌单请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @return 接口响应
     */
    @DeleteMapping("/playlists/{id}") public Map<String, String> deletePlaylist(@PathVariable long id) { return userMusicService.deletePlaylist(id); }
    /**
     * 接收将歌曲加入歌单请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @param songId 歌曲 ID
     * @return 接口响应
     */
    @PostMapping("/playlists/{id}/songs/{songId}") public Map<String, String> addSong(@PathVariable long id, @PathVariable long songId) { return userMusicService.addSong(id, songId); }
    /**
     * 接收从歌单移除歌曲请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @param songId 歌曲 ID
     * @return 接口响应
     */
    @DeleteMapping("/playlists/{id}/songs/{songId}") public Map<String, String> removeSong(@PathVariable long id, @PathVariable long songId) { return userMusicService.removeSong(id, songId); }
    /**
     * 接收批量移除歌单歌曲请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @param songIds songIds 参数
     * @return 接口响应
     */
    @DeleteMapping("/playlists/{id}/songs") public Map<String, Object> removeSongs(@PathVariable long id, @RequestBody List<Long> songIds) { return userMusicService.removeSongs(id, songIds); }
    /**
     * 接收查询我的会员申请请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @GetMapping("/membership-request") public Map<String, Object> membershipRequest() { return userMusicService.membershipRequest(); }
    /**
     * 接收提交会员申请请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @PostMapping("/membership-request") public Map<String, String> applyMembership() { return userMusicService.applyMembership(); }

    /**
     * 接收提交播放记录请求，调用对应业务服务并返回接口响应。

     * @param body 已解析的请求内容
     * @return 接口响应
     */
    @PostMapping("/play-events") public Map<String, String> playEvent(@Valid @RequestBody PlayEventRequest body) { return userMusicService.playEvent(body); }
    /**
     * 接收分页查询播放历史请求，调用对应业务服务并返回接口响应。
     * @param page 从 1 开始的页码
     * @param size 每页记录数
     * @param keyword 可选搜索关键词
     * @return 接口响应
     */
    @GetMapping("/history") public PageResult<Map<String, Object>> history(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String keyword) { return userMusicService.history(page, size, keyword); }
}
