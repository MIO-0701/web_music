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
import com.shengyu.music.persistence.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在线推荐业务层：汇总 MySQL 播放与收藏信号，生成推荐并写入 recommendations 表；每日定时重建。 */
@Service
public class RecommendationService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private final RecommendationsMapper recommendations;
    private final SongsMapper songs;
    private final UsersMapper usersMapper;
    private final PlayEventsMapper plays;
    private final SongTagLinksMapper tagLinks;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public RecommendationService(RecommendationsMapper recommendations, SongsMapper songs, UsersMapper usersMapper,
        PlayEventsMapper plays, SongTagLinksMapper tagLinks, StringRedisTemplate redis, ObjectMapper json) {
        this.recommendations = recommendations; this.songs = songs; this.usersMapper = usersMapper;
        this.plays = plays; this.tagLinks = tagLinks; this.redis = redis; this.json = json;
    }

    /**
     * 重新生成全部用户推荐。
     * @param algorithm 推荐算法标识
     * @return 处理的用户数量
     */
    public List<Map<String, Object>> mine(String algorithm) {
        if (!List.of("TAG", "ITEM_CF").contains(algorithm)) throw ApiException.badRequest("推荐算法须为 TAG 或 ITEM_CF");
        long user = AuthService.current().id();
        String key = "recommend:" + user + ":" + algorithm;
        try { String cached = redis.opsForValue().get(key); if (cached != null) return json.readValue(cached, new TypeReference<>() {}); } catch (Exception ignored) { }
        var rows = recommendations.forUser(user, algorithm);
        if (rows.isEmpty()) {
            rows = songs.fallbackRecommendations();
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
        var yesterday = plays.yesterdaySignals(user).stream().map(row -> new RecommendationEngine.Interaction(user, ((Number) row.get("song_id")).longValue(), ((Number) row.get("weight")).doubleValue())).toList();
        List<Map<String, Object>> rows;
        if (yesterday.isEmpty()) {
            rows = songs.yesterdayPopular();
        } else {
            var catalog = songs.selectMaps(Wrappers.<Songs>query().select("id","title","artist","album_name","genre","language_code","duration_seconds","vip","play_count").eq("published", true));
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
            tagLinks.publishedTags()
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

    /**
     * 生成每日推荐。
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Shanghai")
    public void daily() { rebuild(); }

    /**
     * 重新生成全部用户推荐。
     * 处理流程：读取用户交互与候选歌曲，执行标签和物品协同过滤排序，替换 MySQL 中的推荐结果。
     * @return 处理的用户数量
     */
    @Transactional
    public synchronized int rebuild() {
        List<Long> users = usersMapper.selectList(Wrappers.<Users>query().select("id").eq("role", "USER").eq("enabled", true)).stream().map(user -> user.id).toList();
        Set<Long> candidates = new HashSet<>(songs.selectList(Wrappers.<Songs>query().select("id").eq("published", true)).stream().map(song -> song.id).toList());
        Map<Long, Set<String>> tags = new HashMap<>();
        songs.selectMaps(Wrappers.<Songs>query().select("id","genre","language_code").eq("published", true)).forEach(row -> {
            long id = ((Number) row.get("id")).longValue();
            tags.computeIfAbsent(id, ignored -> new HashSet<>()).add("genre:" + row.get("genre"));
            tags.get(id).add("language:" + row.get("language_code"));
        });
        tagLinks.publishedTags()
            .forEach(row -> tags.computeIfAbsent(((Number) row.get("song_id")).longValue(), ignored -> new HashSet<>()).add("tag:" + row.get("name")));
        Map<Long, Double> popularity = new HashMap<>();
        songs.selectMaps(Wrappers.<Songs>query().select("id","play_count").eq("published", true))
            .forEach(row -> popularity.put(((Number) row.get("id")).longValue(), ((Number) row.get("play_count")).doubleValue()));
        var interactions = loadInteractions();
        var itemModel = RecommendationEngine.buildItemModel(interactions);
        LocalDateTime now = LocalDateTime.now();
        for (long user : users) {
            recommendations.delete(Wrappers.<Recommendations>query().eq("user_id", user));
            save(user, "TAG", RecommendationEngine.byTags(user, candidates, interactions, tags, popularity, 20), now);
            save(user, "ITEM_CF", RecommendationEngine.itemCollaborative(user, candidates, interactions, popularity, itemModel, 20), now);
            try { redis.delete(List.of("recommend:" + user + ":TAG", "recommend:" + user + ":ITEM_CF")); } catch (Exception ignored) { }
        }
        log.info("Daily recommendations built for {} users and {} songs", users.size(), candidates.size());
        return users.size();
    }

    /**
     * 加载用户交互数据。
     * @return 操作结果
     */
    private List<RecommendationEngine.Interaction> loadInteractions() {
        return plays.interactions().stream().map(row -> new RecommendationEngine.Interaction(((Number) row.get("user_id")).longValue(), ((Number) row.get("song_id")).longValue(), ((Number) row.get("weight")).doubleValue())).toList();
    }

    /**
     * 保存推荐结果。
     * @param user user 参数
     * @param algorithm 推荐算法标识
     * @param ranked ranked 参数
     * @param now now 参数
     */
    private void save(long user, String algorithm, List<RecommendationEngine.Ranked> ranked, LocalDateTime now) {
        for (var item : ranked) {
            Recommendations entry = new Recommendations(); entry.userId = user; entry.songId = item.itemId();
            entry.algorithm = algorithm; entry.score = item.score(); entry.reason = item.reason(); entry.generatedAt = now;
            recommendations.insert(entry);
        }
    }
}
