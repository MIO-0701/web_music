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

    public PageResult<Map<String, Object>> favorites(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        long user = AuthService.current().id(); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        String term = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = favoritesMapper.countVisible(user, term);
        var rows = favoritesMapper.listVisible(user, term, s, (p - 1) * s);
        rows.forEach(row -> row.put("coverUrl", "/api/songs/" + row.get("id") + "/cover"));
        return new PageResult<>(rows, total, p, s);
    }

    public Map<String, String> favorite(@PathVariable long songId) {
        catalog.song(songId, false);
        favoritesMapper.insertIgnore(AuthService.current().id(), songId);
        return Map.of("message", "已收藏");
    }
    public Map<String, String> unfavorite(@PathVariable long songId) {
        favoritesMapper.delete(Wrappers.<Favorites>query().eq("user_id", AuthService.current().id()).eq("song_id", songId));
        return Map.of("message", "已取消收藏");
    }

    public record PlaylistRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}
    public List<Map<String, Object>> playlists() {
        return playlistsMapper.listForUser(AuthService.current().id());
    }
    public Map<String, Object> createPlaylist(@Valid @RequestBody PlaylistRequest body) {
        Playlists playlist = new Playlists(); playlist.userId = AuthService.current().id(); playlist.name = body.name().trim(); playlist.description = body.description() == null ? "" : body.description().trim(); playlistsMapper.insert(playlist); long id = playlist.id;
        return Map.of("id", id);
    }
    public Map<String, Object> playlist(@PathVariable long id) {
        requirePlaylistOwner(id);
        Playlists playlist = playlistsMapper.selectById(id);
        var visible = playlistSongs.visibleSongs(id);
        visible.forEach(song -> song.put("coverUrl", "/api/songs/" + song.get("id") + "/cover"));
        return Map.of("id", id, "name", playlist.name, "description", playlist.description, "songs", visible);
    }
    public Map<String, String> renamePlaylist(@PathVariable long id, @Valid @RequestBody PlaylistRequest body) {
        requirePlaylistOwner(id); playlistsMapper.update(null, Wrappers.<Playlists>update().eq("id", id).set("name", body.name().trim()).set("description", body.description() == null ? "" : body.description().trim())); return Map.of("message", "已更新");
    }
    public Map<String, String> deletePlaylist(@PathVariable long id) {
        requirePlaylistOwner(id); playlistsMapper.deleteById(id); return Map.of("message", "已删除");
    }
    public Map<String, String> addSong(@PathVariable long id, @PathVariable long songId) {
        requirePlaylistOwner(id); catalog.song(songId, false);
        playlistSongs.insertIgnore(id, songId); return Map.of("message", "已加入歌单");
    }
    public Map<String, String> removeSong(@PathVariable long id, @PathVariable long songId) {
        requirePlaylistOwner(id); playlistSongs.delete(Wrappers.<PlaylistSongs>query().eq("playlist_id", id).eq("song_id", songId)); return Map.of("message", "已移出歌单");
    }
    public Map<String, Object> removeSongs(@PathVariable long id, @RequestBody List<Long> songIds) {
        requirePlaylistOwner(id);
        if (songIds.isEmpty()) return Map.of("removed", 0);
        if (songIds.size() > 200) throw ApiException.badRequest("一次最多移除 200 首歌曲");
        int removed = playlistSongs.delete(Wrappers.<PlaylistSongs>query().eq("playlist_id", id).in("song_id", songIds));
        return Map.of("removed", removed);
    }
    private void requirePlaylistOwner(long id) {
        if (playlistsMapper.selectCount(Wrappers.<Playlists>query().eq("id", id).eq("user_id", AuthService.current().id())) == 0)
            throw ApiException.notFound("歌单不存在");
    }

    public Map<String, Object> membershipRequest() {
        var row = requests.latestForUser(AuthService.current().id());
        return row == null ? Map.of("status", "NONE") : row;
    }
    public Map<String, String> applyMembership() {
        var user = AuthService.current();
        if (!user.role().equals("USER")) throw ApiException.badRequest("管理员无需申请会员");
        if (user.membershipExpiresAt() != null && user.membershipExpiresAt().isAfter(java.time.LocalDateTime.now())) throw ApiException.badRequest("会员资格仍有效");
        if (requests.selectCount(Wrappers.<MembershipRequests>query().eq("user_id", user.id()).eq("status", "PENDING")) > 0) throw ApiException.badRequest("申请正在审核中");
        MembershipRequests request = new MembershipRequests(); request.userId = user.id(); requests.insert(request);
        return Map.of("message", "申请已提交");
    }

    public record PlayEventRequest(long songId, @Min(10) @Max(7200) int secondsPlayed, boolean completed) {}
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
    public PageResult<Map<String, Object>> history(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String keyword) {
        long user = AuthService.current().id(); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        String term = keyword.isBlank() ? null : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        long total = plays.historyCount(user, term);
        var rows = plays.history(user, term, s, (p - 1) * s);
        rows.forEach(row -> row.put("coverUrl", "/api/songs/" + row.get("song_id") + "/cover"));
        return new PageResult<>(rows, total, p, s);
    }
}
