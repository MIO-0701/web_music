package com.shengyu.music.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public CatalogService(JdbcTemplate jdbc, StringRedisTemplate redis, ObjectMapper json) { this.jdbc = jdbc; this.redis = redis; this.json = json; }

    public PageResult<Map<String, Object>> search(String keyword, String genre, String language, Boolean vip, int page, int size, boolean admin) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        StringBuilder where = new StringBuilder(admin ? " WHERE 1=1" : " WHERE published=TRUE");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) { where.append(" AND (title LIKE ? OR artist LIKE ? OR album_name LIKE ?)"); String term = "%" + keyword.trim() + "%"; args.add(term); args.add(term); args.add(term); }
        if (genre != null && !genre.isBlank()) { where.append(" AND genre=?"); args.add(genre); }
        if (language != null && !language.isBlank()) { where.append(" AND language_code=?"); args.add(language); }
        if (vip != null) { where.append(" AND vip=?"); args.add(vip); }
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM songs" + where, Long.class, args.toArray());
        List<Object> queryArgs = new ArrayList<>(args); queryArgs.add(s); queryArgs.add((p - 1) * s);
        var rows = jdbc.queryForList("SELECT id,title,artist,album_name,language_code,genre,description,duration_seconds,vip,published,play_count,created_at FROM songs" + where + " ORDER BY id DESC LIMIT ? OFFSET ?", queryArgs.toArray());
        return new PageResult<>(rows.stream().map(this::publicSong).toList(), total, p, s);
    }

    public List<Map<String, Object>> genres() {
        return jdbc.queryForList("SELECT genre,COUNT(*) song_count FROM songs WHERE published=TRUE AND genre<>'' AND genre<>'未分类' GROUP BY genre ORDER BY song_count DESC,genre");
    }

    public Map<String, Object> song(long id, boolean admin) {
        var rows = jdbc.queryForList("SELECT id,title,artist,album_name,language_code,genre,description,duration_seconds,vip,published,play_count,created_at FROM songs WHERE id=?" + (admin ? "" : " AND published=TRUE"), id);
        if (rows.isEmpty()) throw ApiException.notFound("歌曲不存在或已下架");
        var result = publicSong(rows.getFirst());
        result.put("tags", jdbc.queryForList("SELECT t.name FROM song_tags t JOIN song_tag_links l ON l.tag_id=t.id WHERE l.song_id=? ORDER BY t.name", String.class, id));
        return result;
    }

    public Map<String, Object> raw(long id) {
        var rows = jdbc.queryForList("SELECT * FROM songs WHERE id=?", id);
        if (rows.isEmpty()) throw ApiException.notFound("歌曲不存在");
        return rows.getFirst();
    }

    public List<Map<String, Object>> hot() {
        try {
            String cached = redis.opsForValue().get("catalog:hot");
            if (cached != null) return json.readValue(cached, new TypeReference<>() {});
        } catch (Exception ignored) { }
        var list = jdbc.queryForList("SELECT id,title,artist,album_name,language_code,genre,description,duration_seconds,vip,published,play_count,created_at FROM songs WHERE published=TRUE ORDER BY play_count DESC,id DESC LIMIT 12")
            .stream().map(this::publicSong).toList();
        try { redis.opsForValue().set("catalog:hot", json.writeValueAsString(list), 10, TimeUnit.MINUTES); } catch (Exception ignored) { }
        return list;
    }

    public void invalidateHot() { try { redis.delete("catalog:hot"); } catch (Exception ignored) { } }

    private Map<String, Object> publicSong(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>(row);
        long id = ((Number) row.get("id")).longValue();
        out.put("coverUrl", "/api/songs/" + id + "/cover");
        out.put("audioUrl", "/api/songs/" + id + "/audio");
        return out;
    }
}
