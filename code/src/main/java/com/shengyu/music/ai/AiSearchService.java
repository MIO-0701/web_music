package com.shengyu.music.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shengyu.music.auth.AuthService;
import com.shengyu.music.common.ApiException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

@Service
public class AiSearchService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    private final DeepSeekClient deepSeek;
    public AiSearchService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, ObjectMapper json, StringRedisTemplate redis, DeepSeekClient deepSeek) {
        this.jdbc = jdbc; this.named = named; this.json = json; this.redis = redis; this.deepSeek = deepSeek;
    }

    public List<Map<String, Object>> conversations() {
        return jdbc.queryForList("SELECT id,title,created_at,updated_at FROM ai_conversations WHERE user_id=? ORDER BY updated_at DESC LIMIT 50", AuthService.current().id());
    }
    public List<Map<String, Object>> messages(long conversationId) {
        requireOwner(conversationId);
        return jdbc.queryForList("SELECT id,role,content,parsed_conditions,result_song_ids,status,created_at FROM ai_messages WHERE conversation_id=? ORDER BY id", conversationId);
    }

    public Map<String, Object> chat(Long conversationId, String message) {
        long user = AuthService.current().id();
        String text = message == null ? "" : message.trim();
        if (text.isBlank() || text.length() > 500) throw ApiException.badRequest("描述须为 1–500 字");
        String quotaKey = "ai:quota:" + user + ":" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long used = redis.opsForValue().increment(quotaKey);
        if (used != null && used == 1) redis.expire(quotaKey, Duration.ofDays(2));
        if (used != null && used > 30) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "今天的 AI 查歌次数已用完");
        long id = conversationId == null ? createConversation(user, text) : conversationId;
        if (conversationId != null) requireOwner(id);
        jdbc.update("INSERT INTO ai_messages(conversation_id,role,content) VALUES(?,'USER',?)", id, text);
        var recent = jdbc.queryForList("SELECT role,content FROM ai_messages WHERE conversation_id=? AND status='OK' ORDER BY id DESC LIMIT 6", id);
        Collections.reverse(recent);
        List<Map<String, String>> history = recent.stream().map(row -> Map.of("role", "USER".equals(row.get("role")) ? "user" : "assistant", "content", String.valueOf(row.get("content")))).toList();
        try {
            JsonNode conditions = deepSeek.parse(history);
            String keyword = safe(conditions.path("keyword").asText(""), 100);
            String genre = safe(conditions.path("genre").asText(""), 80);
            String language = safe(conditions.path("language").asText(""), 12);
            String artist = safe(conditions.path("artist").asText(""), 100);
            if (!List.of("", "cn", "en", "jp", "fr", "ru").contains(language)) language = "";
            List<Object> args = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT DISTINCT s.id,s.title,s.artist,s.album_name,s.genre,s.language_code,s.duration_seconds,s.vip,s.play_count FROM songs s LEFT JOIN song_tag_links st ON st.song_id=s.id LEFT JOIN song_tags t ON t.id=st.tag_id WHERE s.published=TRUE");
            if (!keyword.isBlank()) { sql.append(" AND (s.title LIKE ? OR s.artist LIKE ? OR s.album_name LIKE ? OR s.description LIKE ? OR t.name LIKE ?)"); String value = "%" + keyword + "%"; for (int x = 0; x < 5; x++) args.add(value); }
            if (!genre.isBlank()) { sql.append(" AND (s.genre LIKE ? OR t.name LIKE ?)"); args.add("%" + genre + "%"); args.add("%" + genre + "%"); }
            if (!language.isBlank()) { sql.append(" AND s.language_code=?"); args.add(language); }
            if (!artist.isBlank()) { sql.append(" AND s.artist LIKE ?"); args.add("%" + artist + "%"); }
            sql.append(" ORDER BY s.play_count DESC,s.id DESC LIMIT 10");
            var songs = jdbc.queryForList(sql.toString(), args.toArray());
            songs.forEach(song -> song.put("coverUrl", "/api/songs/" + song.get("id") + "/cover"));
            String reply = safe(conditions.path("reply").asText("找到这些歌曲"), 200);
            if (reply.isBlank()) reply = "找到这些歌曲";
            if (songs.isEmpty()) reply = "本地曲库暂时没有符合描述的歌曲，可以换个说法试试。";
            List<Object> ids = songs.stream().map(song -> song.get("id")).toList();
            jdbc.update("INSERT INTO ai_messages(conversation_id,role,content,parsed_conditions,result_song_ids,status) VALUES(?,'ASSISTANT',?,?,?,'OK')",
                id, reply, json.writeValueAsString(Map.of("keyword", keyword, "genre", genre, "language", language, "artist", artist)), json.writeValueAsString(ids));
            jdbc.update("UPDATE ai_conversations SET updated_at=NOW() WHERE id=?", id);
            return Map.of("conversationId", id, "reply", reply, "conditions", Map.of("keyword", keyword, "genre", genre, "language", language, "artist", artist), "songs", songs);
        } catch (ApiException ex) {
            jdbc.update("INSERT INTO ai_messages(conversation_id,role,content,status) VALUES(?,'ASSISTANT',?,'ERROR')", id, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            jdbc.update("INSERT INTO ai_messages(conversation_id,role,content,status) VALUES(?,'ASSISTANT',?,'ERROR')", id, "AI 查歌失败，请稍后重试");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 查歌失败，请稍后重试");
        }
    }

    private long createConversation(long user, String title) {
        var keys = new GeneratedKeyHolder();
        named.update("INSERT INTO ai_conversations(user_id,title) VALUES(:user,:title)", new MapSqlParameterSource("user", user).addValue("title", safe(title, 100)), keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private void requireOwner(long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ai_conversations WHERE id=? AND user_id=?", Long.class, id, AuthService.current().id()) == 0)
            throw ApiException.notFound("会话不存在");
    }
    private static String safe(String value, int max) { return value == null ? "" : value.trim().substring(0, Math.min(value.trim().length(), max)); }
}
