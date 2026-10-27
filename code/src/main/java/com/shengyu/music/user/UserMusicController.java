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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class UserMusicController {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final StringRedisTemplate redis;
    private final CatalogService catalog;
    public UserMusicController(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, StringRedisTemplate redis, CatalogService catalog) {
        this.jdbc = jdbc; this.named = named; this.redis = redis; this.catalog = catalog;
    }

    @GetMapping("/favorites") public PageResult<Map<String, Object>> favorites(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        long user = AuthService.current().id(); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        String where = " FROM favorites f JOIN songs s ON s.id=f.song_id WHERE f.user_id=? AND s.published=TRUE";
        var args = new ArrayList<Object>(); args.add(user);
        if (keyword != null && !keyword.isBlank()) {
            where += " AND (s.title LIKE ? OR s.artist LIKE ? OR s.album_name LIKE ?)";
            String term = "%" + keyword.trim() + "%";
            args.add(term); args.add(term); args.add(term);
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        args.add(s); args.add((p - 1) * s);
        var rows = jdbc.queryForList("SELECT s.id,s.title,s.artist,s.album_name,s.genre,s.duration_seconds,s.vip,s.play_count,f.created_at" + where + " ORDER BY f.created_at DESC,s.id DESC LIMIT ? OFFSET ?", args.toArray());
        rows.forEach(row -> row.put("coverUrl", "/api/songs/" + row.get("id") + "/cover"));
        return new PageResult<>(rows, total, p, s);
    }

    @PostMapping("/favorites/{songId}") public Map<String, String> favorite(@PathVariable long songId) {
        catalog.song(songId, false);
        jdbc.update("INSERT IGNORE INTO favorites(user_id,song_id) VALUES(?,?)", AuthService.current().id(), songId);
        return Map.of("message", "已收藏");
    }
    @DeleteMapping("/favorites/{songId}") public Map<String, String> unfavorite(@PathVariable long songId) {
        jdbc.update("DELETE FROM favorites WHERE user_id=? AND song_id=?", AuthService.current().id(), songId);
        return Map.of("message", "已取消收藏");
    }

    public record PlaylistRequest(@NotBlank @Size(max = 100) String name) {}
    @GetMapping("/playlists") public List<Map<String, Object>> playlists() {
        return jdbc.queryForList("SELECT p.id,p.name,p.created_at,COUNT(visible.id) song_count," +
            "(SELECT ps2.song_id FROM playlist_songs ps2 JOIN songs s2 ON s2.id=ps2.song_id AND s2.published=TRUE WHERE ps2.playlist_id=p.id ORDER BY ps2.added_at DESC,ps2.song_id DESC LIMIT 1) cover_song_id " +
            "FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlist_id=p.id LEFT JOIN songs visible ON visible.id=ps.song_id AND visible.published=TRUE WHERE p.user_id=? GROUP BY p.id,p.name,p.created_at ORDER BY p.id DESC", AuthService.current().id());
    }
    @PostMapping("/playlists") public Map<String, Object> createPlaylist(@Valid @RequestBody PlaylistRequest body) {
        var keys = new GeneratedKeyHolder();
        named.update("INSERT INTO playlists(user_id,name) VALUES(:user,:name)", new MapSqlParameterSource("user", AuthService.current().id()).addValue("name", body.name().trim()), keys);
        return Map.of("id", Objects.requireNonNull(keys.getKey()).longValue());
    }
    @GetMapping("/playlists/{id}") public Map<String, Object> playlist(@PathVariable long id) {
        requirePlaylistOwner(id);
        var name = jdbc.queryForObject("SELECT name FROM playlists WHERE id=?", String.class, id);
        var songs = jdbc.queryForList("SELECT s.id,s.title,s.artist,s.album_name,s.genre,s.duration_seconds,s.vip FROM playlist_songs ps JOIN songs s ON s.id=ps.song_id WHERE ps.playlist_id=? AND s.published=TRUE ORDER BY ps.added_at DESC", id);
        songs.forEach(song -> song.put("coverUrl", "/api/songs/" + song.get("id") + "/cover"));
        return Map.of("id", id, "name", name, "songs", songs);
    }
    @PutMapping("/playlists/{id}") public Map<String, String> renamePlaylist(@PathVariable long id, @Valid @RequestBody PlaylistRequest body) {
        requirePlaylistOwner(id); jdbc.update("UPDATE playlists SET name=? WHERE id=?", body.name().trim(), id); return Map.of("message", "已更新");
    }
    @DeleteMapping("/playlists/{id}") public Map<String, String> deletePlaylist(@PathVariable long id) {
        requirePlaylistOwner(id); jdbc.update("DELETE FROM playlists WHERE id=?", id); return Map.of("message", "已删除");
    }
    @PostMapping("/playlists/{id}/songs/{songId}") public Map<String, String> addSong(@PathVariable long id, @PathVariable long songId) {
        requirePlaylistOwner(id); catalog.song(songId, false);
        jdbc.update("INSERT IGNORE INTO playlist_songs(playlist_id,song_id) VALUES(?,?)", id, songId); return Map.of("message", "已加入歌单");
    }
    @DeleteMapping("/playlists/{id}/songs/{songId}") public Map<String, String> removeSong(@PathVariable long id, @PathVariable long songId) {
        requirePlaylistOwner(id); jdbc.update("DELETE FROM playlist_songs WHERE playlist_id=? AND song_id=?", id, songId); return Map.of("message", "已移出歌单");
    }
    private void requirePlaylistOwner(long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM playlists WHERE id=? AND user_id=?", Long.class, id, AuthService.current().id()) == 0)
            throw ApiException.notFound("歌单不存在");
    }

    public record PlayEventRequest(long songId, @Min(10) @Max(7200) int secondsPlayed, boolean completed) {}
    @PostMapping("/play-events") public Map<String, String> playEvent(@Valid @RequestBody PlayEventRequest body) {
        var user = AuthService.current();
        var song = catalog.raw(body.songId());
        if (!Boolean.TRUE.equals(song.get("published"))) throw ApiException.notFound("歌曲已下架");
        int duration = ((Number) song.get("duration_seconds")).intValue();
        int limit = Boolean.TRUE.equals(song.get("vip")) && !user.member() && !"ADMIN".equals(user.role()) ? 30 : duration;
        if (limit > 0 && body.secondsPlayed() > limit + 3) throw ApiException.badRequest("播放时长超过可播放范围");
        String key = "play:dedupe:" + user.id() + ":" + body.songId();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(30)))) return Map.of("message", "播放记录已接收");
        jdbc.update("INSERT INTO play_events(user_id,song_id,seconds_played,completed) VALUES(?,?,?,?)", user.id(), body.songId(), body.secondsPlayed(), body.completed());
        jdbc.update("UPDATE songs SET play_count=play_count+1 WHERE id=?", body.songId());
        catalog.invalidateHot();
        return Map.of("message", "播放记录已保存");
    }
    @GetMapping("/history") public PageResult<Map<String, Object>> history(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        long user = AuthService.current().id(); int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        long total = jdbc.queryForObject("SELECT COUNT(DISTINCT e.song_id) FROM play_events e JOIN songs s ON s.id=e.song_id WHERE e.user_id=? AND s.published=TRUE", Long.class, user);
        var rows = jdbc.queryForList("SELECT e.id event_id,e.seconds_played,e.completed,e.created_at,s.id song_id,s.title,s.artist,s.album_name,s.genre,s.duration_seconds,s.vip " +
            "FROM (SELECT song_id,MAX(id) latest_id FROM play_events WHERE user_id=? GROUP BY song_id) latest " +
            "JOIN play_events e ON e.id=latest.latest_id JOIN songs s ON s.id=e.song_id AND s.published=TRUE " +
            "ORDER BY e.id DESC LIMIT ? OFFSET ?", user, s, (p - 1) * s);
        rows.forEach(row -> row.put("coverUrl", "/api/songs/" + row.get("song_id") + "/cover"));
        return new PageResult<>(rows, total, p, s);
    }
}
