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
import com.shengyu.music.persistence.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

/** AI 查歌业务层：保存 MySQL 对话和消息，使用 Redis 限流，解析模型条件后只查询本地已上架歌曲。 */
@Service
public class AiSearchService {
    private final AiConversationsMapper conversations;
    private final AiMessagesMapper messages;
    private final SongsMapper songsMapper;
    private final ObjectMapper json;
    private final StringRedisTemplate redis;
    private final DeepSeekClient deepSeek;
    public AiSearchService(AiConversationsMapper conversations, AiMessagesMapper messages, SongsMapper songsMapper, ObjectMapper json, StringRedisTemplate redis, DeepSeekClient deepSeek) {
        this.conversations = conversations; this.messages = messages; this.songsMapper = songsMapper; this.json = json; this.redis = redis; this.deepSeek = deepSeek;
    }

    /**
     * 处理自然语言查歌消息。
     * @return 会话、解析条件和匹配歌曲结果
     */
    public List<Map<String, Object>> conversations() {
        return conversations.selectMaps(Wrappers.<AiConversations>query().select("id","title","created_at","updated_at").eq("user_id", AuthService.current().id()).orderByDesc("updated_at").last("LIMIT 50"));
    }
    /**
     * 查询指定会话的消息历史。
     * @param conversationId conversationId 参数
     * @return 会话、解析条件和匹配歌曲结果
     */
    public List<Map<String, Object>> messages(long conversationId) {
        requireOwner(conversationId);
        return messages.selectMaps(Wrappers.<AiMessages>query().select("id","role","content","parsed_conditions","result_song_ids","status","created_at").eq("conversation_id", conversationId).orderByAsc("id"));
    }

    /**
     * 处理自然语言查歌消息。
     * 处理流程：校验会话归属和每日限额，保存对话；调用模型解析条件，再查询本地歌曲并保存响应。
     * @param conversationId conversationId 参数
     * @param message 用户输入的消息
     * @return 会话、解析条件和匹配歌曲结果
     */
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
        AiMessages userMessage = new AiMessages(); userMessage.conversationId = id; userMessage.role = "USER"; userMessage.content = text; messages.insert(userMessage);
        var recent = messages.selectMaps(Wrappers.<AiMessages>query().select("role","content").eq("conversation_id", id).eq("status", "OK").orderByDesc("id").last("LIMIT 6"));
        Collections.reverse(recent);
        List<Map<String, String>> history = recent.stream().map(row -> Map.of("role", "USER".equals(row.get("role")) ? "user" : "assistant", "content", String.valueOf(row.get("content")))).toList();
        try {
            JsonNode conditions = deepSeek.parse(history);
            String keyword = safe(conditions.path("keyword").asText(""), 100);
            String genre = safe(conditions.path("genre").asText(""), 80);
            String language = safe(conditions.path("language").asText(""), 12);
            String artist = safe(conditions.path("artist").asText(""), 100);
            if (!List.of("", "cn", "en", "jp", "fr", "ru").contains(language)) language = "";
            var songs = songsMapper.aiSearch(keyword, genre, language, artist);
            songs.forEach(song -> song.put("coverUrl", "/api/songs/" + song.get("id") + "/cover"));
            String reply = safe(conditions.path("reply").asText("找到这些歌曲"), 200);
            if (reply.isBlank()) reply = "找到这些歌曲";
            if (songs.isEmpty()) reply = "本地曲库暂时没有符合描述的歌曲，可以换个说法试试。";
            List<Object> ids = songs.stream().map(song -> song.get("id")).toList();
            AiMessages response = new AiMessages(); response.conversationId = id; response.role = "ASSISTANT"; response.content = reply;
            response.parsedConditions = json.writeValueAsString(Map.of("keyword", keyword, "genre", genre, "language", language, "artist", artist));
            response.resultSongIds = json.writeValueAsString(ids); response.status = "OK"; messages.insert(response);
            conversations.update(null, Wrappers.<AiConversations>update().eq("id", id).set("updated_at", LocalDateTime.now()));
            return Map.of("conversationId", id, "reply", reply, "conditions", Map.of("keyword", keyword, "genre", genre, "language", language, "artist", artist), "songs", songs);
        } catch (ApiException ex) {
            saveError(id, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            saveError(id, "AI 查歌失败，请稍后重试");
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 查歌失败，请稍后重试");
        }
    }

    /**
     * 为当前用户创建 AI 会话。
     * @param user user 参数
     * @param title 歌曲标题
     * @return 操作结果
     */
    private long createConversation(long user, String title) {
        AiConversations conversation = new AiConversations(); conversation.userId = user; conversation.title = safe(title, 100); conversations.insert(conversation); return conversation.id;
    }
    /**
     * 验证当前用户拥有该资源。
     * @param id 记录 ID
     */
    private void requireOwner(long id) {
        if (conversations.selectCount(Wrappers.<AiConversations>query().eq("id", id).eq("user_id", AuthService.current().id())) == 0)
            throw ApiException.notFound("会话不存在");
    }
    /**
     * 保存失败消息。
     * @param id 记录 ID
     * @param message 用户输入的消息
     */
    private void saveError(long id, String message) {
        AiMessages error = new AiMessages(); error.conversationId = id; error.role = "ASSISTANT"; error.content = message; error.status = "ERROR"; messages.insert(error);
    }
    /**
     * 清理并限制输入内容。
     * @param value 待设置的新状态或值
     * @param max max 参数
     * @return 操作结果
     */
    private static String safe(String value, int max) { return value == null ? "" : value.trim().substring(0, Math.min(value.trim().length(), max)); }
}
