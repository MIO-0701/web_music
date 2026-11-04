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
import com.shengyu.music.persistence.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;

/** 用户音乐业务层：处理收藏、歌单、会员申请和播放记录，维护 MySQL 中的用户关联数据。 */
@Service
public class UserMusicService {
    private final FavoritesMapper favoritesMapper;
    private final PlaylistsMapper playlistsMapper;
    private final PlaylistSongsMapper playlistSongs;
    private final MembershipRequestsMapper requests;
    private final PlayEventsMapper plays;
    private final SongsMapper songs;
    private final StringRedisTemplate redis;
    private final CatalogService catalog;
    public UserMusicService(FavoritesMapper favoritesMapper, PlaylistsMapper playlistsMapper, PlaylistSongsMapper playlistSongs,
        MembershipRequestsMapper requests, PlayEventsMapper plays, SongsMapper songs, StringRedisTemplate redis, CatalogService catalog) {
        this.favoritesMapper = favoritesMapper; this.playlistsMapper = playlistsMapper; this.playlistSongs = playlistSongs;
        this.requests = requests; this.plays = plays; this.songs = songs; this.redis = redis; this.catalog = catalog;
    }

    /**
     * 分页查询当前用户收藏。
     * 处理流程：查询 favorites 与已上架歌曲的关联，按关键词筛选并补充封面地址。
     * @param keyword 可选搜索关键词
     * @param page 从 1 开始的页码
     * @param size 每页记录数
     * @return 当前用户的收藏歌曲分页结果
     */
    public PageResult<Map<String, Object>> favorites(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        long user = AuthService.current().id(); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        String term = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = favoritesMapper.countVisible(user, term);
        var rows = favoritesMapper.listVisible(user, term, s, (p - 1) * s);
        rows.forEach(row -> row.put("coverUrl", "/api/songs/" + row.get("id") + "/cover"));
        return new PageResult<>(rows, total, p, s);
    }

    /**
     * 收藏一首已上架歌曲。
     * 处理流程：确认歌曲可见后，以忽略重复的方式写入 favorites 关联。
     * @param songId 歌曲 ID
     * @return 收藏成功消息
     */
    public Map<String, String> favorite(@PathVariable long songId) {
        catalog.song(songId, false);
        favoritesMapper.insertIgnore(AuthService.current().id(), songId);
        return Map.of("message", "已收藏");
    }
    /**
     * 取消歌曲收藏。
     * 处理流程：按当前用户和歌曲 ID 删除 favorites 关联。
     * @param songId 歌曲 ID
     * @return 取消收藏消息
     */
    public Map<String, String> unfavorite(@PathVariable long songId) {
        favoritesMapper.delete(Wrappers.<Favorites>query().eq("user_id", AuthService.current().id()).eq("song_id", songId));
        return Map.of("message", "已取消收藏");
    }

    /** 歌单创建或修改请求，包含名称与描述。 */
    public record PlaylistRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}
    /**
     * 查询当前用户歌单。
     * 处理流程：按认证用户 ID 从 playlists 表读取歌单列表。
     * @return 当前用户的歌单列表
     */
    public List<Map<String, Object>> playlists() {
        return playlistsMapper.listForUser(AuthService.current().id());
    }
    /**
     * 创建个人歌单。
     * 处理流程：整理名称与描述，写入 playlists 表并返回新 ID。
     * @param body 请求内容
     * @return 包含新歌单 ID 的结果
     */
    public Map<String, Object> createPlaylist(@Valid @RequestBody PlaylistRequest body) {
        Playlists playlist = new Playlists(); playlist.userId = AuthService.current().id(); playlist.name = body.name().trim(); playlist.description = body.description() == null ? "" : body.description().trim(); playlistsMapper.insert(playlist); long id = playlist.id;
        return Map.of("id", id);
    }
    /**
     * 查看个人歌单及歌曲。
     * 处理流程：先验证歌单归属，再读取歌单元数据和当前可见的歌曲。
     * @param id 记录 ID
     * @return 歌单详情和歌曲列表
     */
    public Map<String, Object> playlist(@PathVariable long id) {
        requirePlaylistOwner(id);
        Playlists playlist = playlistsMapper.selectById(id);
        var visible = playlistSongs.visibleSongs(id);
        visible.forEach(song -> song.put("coverUrl", "/api/songs/" + song.get("id") + "/cover"));
        return Map.of("id", id, "name", playlist.name, "description", playlist.description, "songs", visible);
    }
    /**
     * 修改个人歌单。
     * 处理流程：验证归属后更新歌单名称和描述。
     * @param id 记录 ID
     * @param body 请求内容
     * @return 更新成功消息
     */
    public Map<String, String> renamePlaylist(@PathVariable long id, @Valid @RequestBody PlaylistRequest body) {
        requirePlaylistOwner(id); playlistsMapper.update(null, Wrappers.<Playlists>update().eq("id", id).set("name", body.name().trim()).set("description", body.description() == null ? "" : body.description().trim())); return Map.of("message", "已更新");
    }
    /**
     * 删除个人歌单。
     * 处理流程：验证归属后删除 playlists 记录；关联歌曲由外键级联清理。
     * @param id 记录 ID
     * @return 删除成功消息
     */
    public Map<String, String> deletePlaylist(@PathVariable long id) {
        requirePlaylistOwner(id); playlistsMapper.deleteById(id); return Map.of("message", "已删除");
    }
    /**
     * 将歌曲加入个人歌单。
     * 处理流程：验证歌单归属和歌曲可见性，忽略重复关联并写入 playlist_songs。
     * @param id 记录 ID
     * @param songId 歌曲 ID
     * @return 加入成功消息
     */
    public Map<String, String> addSong(@PathVariable long id, @PathVariable long songId) {
        requirePlaylistOwner(id); catalog.song(songId, false);
        playlistSongs.insertIgnore(id, songId); return Map.of("message", "已加入歌单");
    }
    /**
     * 从歌单移除单首歌曲。
     * 处理流程：验证歌单归属后删除对应 playlist_songs 关联。
     * @param id 记录 ID
     * @param songId 歌曲 ID
     * @return 移除成功消息
     */
    public Map<String, String> removeSong(@PathVariable long id, @PathVariable long songId) {
        requirePlaylistOwner(id); playlistSongs.delete(Wrappers.<PlaylistSongs>query().eq("playlist_id", id).eq("song_id", songId)); return Map.of("message", "已移出歌单");
    }
    /**
     * 从歌单批量移除歌曲。
     * 处理流程：验证歌单归属与数量上限，按 ID 列表删除关联。
     * @param id 记录 ID
     * @param songIds songIds 参数
     * @return 实际移除的歌曲数量
     */
    public Map<String, Object> removeSongs(@PathVariable long id, @RequestBody List<Long> songIds) {
        requirePlaylistOwner(id);
        if (songIds.isEmpty()) return Map.of("removed", 0);
        if (songIds.size() > 200) throw ApiException.badRequest("一次最多移除 200 首歌曲");
        int removed = playlistSongs.delete(Wrappers.<PlaylistSongs>query().eq("playlist_id", id).in("song_id", songIds));
        return Map.of("removed", removed);
    }
    /**
     * 验证歌单归属。
     * @param id 记录 ID
     */
    private void requirePlaylistOwner(long id) {
        if (playlistsMapper.selectCount(Wrappers.<Playlists>query().eq("id", id).eq("user_id", AuthService.current().id())) == 0)
            throw ApiException.notFound("歌单不存在");
    }

    /**
     * 查看最近一次会员申请。
     * 处理流程：查询当前用户最新的 membership_requests 记录；无记录时返回 NONE。
     * @return 申请状态或 NONE
     */
    public Map<String, Object> membershipRequest() {
        var row = requests.latestForUser(AuthService.current().id());
        return row == null ? Map.of("status", "NONE") : row;
    }
    /**
     * 提交会员申请。
     * 处理流程：拒绝管理员、有效会员和已有待审申请；新增待审核申请。
     * @return 提交成功消息
     */
    public Map<String, String> applyMembership() {
        var user = AuthService.current();
        if (!user.role().equals("USER")) throw ApiException.badRequest("管理员无需申请会员");
        if (user.membershipExpiresAt() != null && user.membershipExpiresAt().isAfter(java.time.LocalDateTime.now())) throw ApiException.badRequest("会员资格仍有效");
        if (requests.selectCount(Wrappers.<MembershipRequests>query().eq("user_id", user.id()).eq("status", "PENDING")) > 0) throw ApiException.badRequest("申请正在审核中");
        MembershipRequests request = new MembershipRequests(); request.userId = user.id(); requests.insert(request);
        return Map.of("message", "申请已提交");
    }

    /** 播放事件请求，包含歌曲、有效播放时长与完成状态。 */
    public record PlayEventRequest(long songId, @Min(10) @Max(7200) int secondsPlayed, boolean completed) {}
    /**
     * 记录有效播放事件。
     * 处理流程：验证歌曲与最短播放时长；限制短时间重复记录，写入 play_events 并更新歌曲播放量。
     * @param body 请求内容
     * @return 播放记录接收或保存结果
     */
    public Map<String, String> playEvent(@Valid @RequestBody PlayEventRequest body) {
        var user = AuthService.current();
        var song = catalog.raw(body.songId());
        if (!Boolean.TRUE.equals(song.get("published"))) throw ApiException.notFound("歌曲已下架");
        int duration = ((Number) song.get("duration_seconds")).intValue();
        int limit = Boolean.TRUE.equals(song.get("vip")) && !user.member() && !"ADMIN".equals(user.role()) ? 30 : duration;
        if (limit > 0 && body.secondsPlayed() > limit + 3) throw ApiException.badRequest("播放时长超过可播放范围");
        String key = "play:dedupe:" + user.id() + ":" + body.songId();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(30)))) return Map.of("message", "播放记录已接收");
        PlayEvents event = new PlayEvents(); event.userId = user.id(); event.songId = body.songId(); event.secondsPlayed = body.secondsPlayed(); event.completed = body.completed(); plays.insert(event);
        songs.incrementPlayCount(body.songId());
        catalog.invalidateHot();
        return Map.of("message", "播放记录已保存");
    }
    /**
     * 分页查询播放历史。
     * 处理流程：按当前用户和关键词查询 play_events，并补充歌曲封面地址。
     * @param page 从 1 开始的页码
     * @param size 每页记录数
     * @param keyword 可选歌曲搜索词
     * @return 播放历史分页结果
     */
    public PageResult<Map<String, Object>> history(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String keyword) {
        long user = AuthService.current().id(); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        String term = keyword.isBlank() ? null : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        long total = plays.historyCount(user, term);
        var rows = plays.history(user, term, s, (p - 1) * s);
        rows.forEach(row -> row.put("coverUrl", "/api/songs/" + row.get("song_id") + "/cover"));
        return new PageResult<>(rows, total, p, s);
    }
}
