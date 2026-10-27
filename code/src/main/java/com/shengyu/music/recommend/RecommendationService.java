package com.shengyu.music.recommend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shengyu.music.auth.AuthService;
import com.shengyu.music.common.ApiException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public RecommendationService(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper json) { this.jdbc = jdbc; this.redis = redis; this.json = json; }

    public List<Map<String, Object>> mine(String algorithm) {
        if (!List.of("TAG", "ITEM_CF").contains(algorithm)) throw ApiException.badRequest("推荐算法须为 TAG 或 ITEM_CF");
        long user = AuthService.current().id();
        String key = "recommend:" + user + ":" + algorithm;
        try { String cached = redis.opsForValue().get(key); if (cached != null) return json.readValue(cached, new TypeReference<>() {}); } catch (Exception ignored) { }
        var rows = jdbc.queryForList("SELECT s.id,s.title,s.artist,s.album_name,s.genre,s.duration_seconds,s.vip,r.score,r.reason,r.generated_at FROM recommendations r JOIN songs s ON s.id=r.song_id WHERE r.user_id=? AND r.algorithm=? AND s.published=TRUE ORDER BY r.score DESC,s.id LIMIT 20", user, algorithm);
        if (rows.isEmpty()) {
            rows = jdbc.queryForList("SELECT id,title,artist,album_name,genre,duration_seconds,vip,play_count score,'当前热门歌曲' reason FROM songs WHERE published=TRUE ORDER BY play_count DESC,id DESC LIMIT 20");
        }
        rows.forEach(row -> row.put("coverUrl", "/api/songs/" + row.get("id") + "/cover"));
        try { redis.opsForValue().set(key, json.writeValueAsString(rows), 20, TimeUnit.MINUTES); } catch (Exception ignored) { }
        return rows;
    }

    /** Today's single user-facing list uses only yesterday's listening signals. */
    public List<Map<String, Object>> dailyForCurrentUser() {
        long user = AuthService.current().id();
        String key = "recommend:daily:" + user + ":" + LocalDate.now(ZoneId.of("Asia/Shanghai"));
        try { String cached = redis.opsForValue().get(key); if (cached != null) return json.readValue(cached, new TypeReference<>() {}); } catch (Exception ignored) { }
        var yesterday = jdbc.query("SELECT song_id,SUM(weight) weight FROM (" +
            "SELECT song_id,LEAST(COUNT(*),5)*1.0 weight FROM play_events WHERE user_id=? AND seconds_played>=10 AND created_at>=CURDATE()-INTERVAL 1 DAY AND created_at<CURDATE() GROUP BY song_id " +
            "UNION ALL SELECT song_id,3.0 weight FROM favorites WHERE user_id=? AND created_at>=CURDATE()-INTERVAL 1 DAY AND created_at<CURDATE()) x GROUP BY song_id",
            (rs, row) -> new RecommendationEngine.Interaction(user, rs.getLong("song_id"), rs.getDouble("weight")), user, user);
        List<Map<String, Object>> rows;
        if (yesterday.isEmpty()) {
            rows = jdbc.queryForList("SELECT s.id,s.title,s.artist,s.album_name,s.genre,s.language_code,s.duration_seconds,s.vip," +
                "COALESCE(h.plays,0) score,'昨日热门歌曲' reason FROM songs s LEFT JOIN " +
                "(SELECT song_id,COUNT(*) plays FROM play_events WHERE created_at>=CURDATE()-INTERVAL 1 DAY AND created_at<CURDATE() GROUP BY song_id) h ON h.song_id=s.id " +
                "WHERE s.published=TRUE ORDER BY score DESC,s.play_count DESC,s.id DESC LIMIT 20");
        } else {
            var catalog = jdbc.queryForList("SELECT id,title,artist,album_name,genre,language_code,duration_seconds,vip,play_count FROM songs WHERE published=TRUE");
            Set<Long> candidates = new HashSet<>();
            Map<Long, Set<String>> tags = new HashMap<>();
            Map<Long, Double> popularity = new HashMap<>();
            Map<Long, Map<String, Object>> byId = new HashMap<>();
            for (var song : catalog) {
                long id = ((Number) song.get("id")).longValue();
                candidates.add(id); byId.put(id, song);
                tags.put(id, new HashSet<>(List.of("genre:" + song.get("genre"), "language:" + song.get("language_code"))));
                popularity.put(id, ((Number) song.get("play_count")).doubleValue());
            }
            jdbc.queryForList("SELECT l.song_id,t.name FROM song_tag_links l JOIN song_tags t ON t.id=l.tag_id JOIN songs s ON s.id=l.song_id WHERE s.published=TRUE")
                .forEach(row -> tags.get(((Number) row.get("song_id")).longValue()).add("tag:" + row.get("name")));
            rows = new ArrayList<>();
            for (var ranked : RecommendationEngine.byTags(user, candidates, yesterday, tags, popularity, 20)) {
                var song = new LinkedHashMap<>(byId.get(ranked.itemId()));
                song.put("score", ranked.score());
                song.put("reason", ranked.reason().startsWith("符合") ? "符合你昨日收听的语言和曲风" : "昨日习惯探索");
                rows.add(song);
            }
        }
        rows.forEach(row -> row.put("coverUrl", "/api/songs/" + row.get("id") + "/cover"));
        try { redis.opsForValue().set(key, json.writeValueAsString(rows), 20, TimeUnit.MINUTES); } catch (Exception ignored) { }
        return rows;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Shanghai")
    public void daily() { rebuild(); }

    @Transactional
    public synchronized int rebuild() {
        List<Long> users = jdbc.queryForList("SELECT id FROM users WHERE role='USER' AND enabled=TRUE", Long.class);
        Set<Long> candidates = new HashSet<>(jdbc.queryForList("SELECT id FROM songs WHERE published=TRUE", Long.class));
        Map<Long, Set<String>> tags = new HashMap<>();
        jdbc.queryForList("SELECT id,genre,language_code FROM songs WHERE published=TRUE").forEach(row -> {
            long id = ((Number) row.get("id")).longValue();
            tags.computeIfAbsent(id, ignored -> new HashSet<>()).add("genre:" + row.get("genre"));
            tags.get(id).add("language:" + row.get("language_code"));
        });
        jdbc.queryForList("SELECT l.song_id,t.name FROM song_tag_links l JOIN song_tags t ON t.id=l.tag_id JOIN songs s ON s.id=l.song_id WHERE s.published=TRUE")
            .forEach(row -> tags.computeIfAbsent(((Number) row.get("song_id")).longValue(), ignored -> new HashSet<>()).add("tag:" + row.get("name")));
        Map<Long, Double> popularity = new HashMap<>();
        jdbc.queryForList("SELECT id,play_count FROM songs WHERE published=TRUE")
            .forEach(row -> popularity.put(((Number) row.get("id")).longValue(), ((Number) row.get("play_count")).doubleValue()));
        var interactions = loadInteractions();
        var itemModel = RecommendationEngine.buildItemModel(interactions);
        LocalDateTime now = LocalDateTime.now();
        for (long user : users) {
            jdbc.update("DELETE FROM recommendations WHERE user_id=?", user);
            save(user, "TAG", RecommendationEngine.byTags(user, candidates, interactions, tags, popularity, 20), now);
            save(user, "ITEM_CF", RecommendationEngine.itemCollaborative(user, candidates, interactions, popularity, itemModel, 20), now);
            try { redis.delete(List.of("recommend:" + user + ":TAG", "recommend:" + user + ":ITEM_CF")); } catch (Exception ignored) { }
        }
        log.info("Daily recommendations built for {} users and {} songs", users.size(), candidates.size());
        return users.size();
    }

    private List<RecommendationEngine.Interaction> loadInteractions() {
        String sql = "SELECT user_id,song_id,SUM(weight) weight FROM (" +
            "SELECT user_id,song_id,LEAST(COUNT(*),5) * 1.0 weight FROM play_events WHERE seconds_played>=10 GROUP BY user_id,song_id " +
            "UNION ALL SELECT user_id,song_id,3.0 weight FROM favorites) x GROUP BY user_id,song_id";
        return jdbc.query(sql, (rs, row) -> new RecommendationEngine.Interaction(rs.getLong("user_id"), rs.getLong("song_id"), rs.getDouble("weight")));
    }

    private void save(long user, String algorithm, List<RecommendationEngine.Ranked> ranked, LocalDateTime now) {
        for (var item : ranked) jdbc.update("INSERT INTO recommendations(user_id,song_id,algorithm,score,reason,generated_at) VALUES(?,?,?,?,?,?)",
            user, item.itemId(), algorithm, item.score(), item.reason(), Timestamp.valueOf(now));
    }
}
