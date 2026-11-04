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
@RestController
@RequestMapping("/api/me")
public class UserMusicController {
    private final UserMusicService userMusicService;
    public UserMusicController(UserMusicService service) { this.userMusicService = service; }
    @GetMapping("/favorites") public PageResult<Map<String, Object>> favorites(@RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) { return userMusicService.favorites(keyword, page, size); }

    @PostMapping("/favorites/{songId}") public Map<String, String> favorite(@PathVariable long songId) { return userMusicService.favorite(songId); }
    @DeleteMapping("/favorites/{songId}") public Map<String, String> unfavorite(@PathVariable long songId) { return userMusicService.unfavorite(songId); }

    @GetMapping("/playlists") public List<Map<String, Object>> playlists() { return userMusicService.playlists(); }
    @PostMapping("/playlists") public Map<String, Object> createPlaylist(@Valid @RequestBody PlaylistRequest body) { return userMusicService.createPlaylist(body); }
    @GetMapping("/playlists/{id}") public Map<String, Object> playlist(@PathVariable long id) { return userMusicService.playlist(id); }
    @PutMapping("/playlists/{id}") public Map<String, String> renamePlaylist(@PathVariable long id, @Valid @RequestBody PlaylistRequest body) { return userMusicService.renamePlaylist(id, body); }
    @DeleteMapping("/playlists/{id}") public Map<String, String> deletePlaylist(@PathVariable long id) { return userMusicService.deletePlaylist(id); }
    @PostMapping("/playlists/{id}/songs/{songId}") public Map<String, String> addSong(@PathVariable long id, @PathVariable long songId) { return userMusicService.addSong(id, songId); }
    @DeleteMapping("/playlists/{id}/songs/{songId}") public Map<String, String> removeSong(@PathVariable long id, @PathVariable long songId) { return userMusicService.removeSong(id, songId); }
    @DeleteMapping("/playlists/{id}/songs") public Map<String, Object> removeSongs(@PathVariable long id, @RequestBody List<Long> songIds) { return userMusicService.removeSongs(id, songIds); }
    @GetMapping("/membership-request") public Map<String, Object> membershipRequest() { return userMusicService.membershipRequest(); }
    @PostMapping("/membership-request") public Map<String, String> applyMembership() { return userMusicService.applyMembership(); }

    @PostMapping("/play-events") public Map<String, String> playEvent(@Valid @RequestBody PlayEventRequest body) { return userMusicService.playEvent(body); }
    @GetMapping("/history") public PageResult<Map<String, Object>> history(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "") String keyword) { return userMusicService.history(page, size, keyword); }
}
