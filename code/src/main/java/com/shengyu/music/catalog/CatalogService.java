package com.shengyu.music.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shengyu.music.common.ApiException;
import com.shengyu.music.common.PageResult;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.shengyu.music.persistence.Songs;
import com.shengyu.music.persistence.SongsMapper;
import com.shengyu.music.persistence.SongTagsMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final SongsMapper songs;
    private final SongTagsMapper tags;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    public CatalogService(SongsMapper songs, SongTagsMapper tags, StringRedisTemplate redis, ObjectMapper json) { this.songs = songs; this.tags = tags; this.redis = redis; this.json = json; }

    public PageResult<Map<String, Object>> search(String keyword, String genre, String language, Boolean vip, int page, int size, boolean admin) {
        int p = PageResult.safePage(page), s = PageResult.safeSize(size);
        QueryWrapper<Songs> query = Wrappers.query();
        if (!admin) query.eq("published", true);
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like("title", keyword.trim()).or().like("artist", keyword.trim()).or().like("album_name", keyword.trim()));
        if (genre != null && !genre.isBlank()) query.eq("genre", genre);
        if (language != null && !language.isBlank()) query.eq("language_code", language);
        if (vip != null) query.eq("vip", vip);
        long total = songs.selectCount(query);
        query.select("id","title","artist","album_name","language_code","genre","description","duration_seconds","vip","published","play_count","created_at").orderByDesc("id").last("LIMIT " + s + " OFFSET " + ((p - 1) * s));
        var rows = songs.selectMaps(query);
        return new PageResult<>(rows.stream().map(this::publicSong).toList(), total, p, s);
    }

    public List<Map<String, Object>> genres() {
        return songs.selectMaps(Wrappers.<Songs>query().select("genre", "COUNT(*) AS song_count").eq("published", true).ne("genre", "").ne("genre", "未分类").groupBy("genre").orderByDesc("song_count").orderByAsc("genre"));
    }

    public Map<String, Object> song(long id, boolean admin) {
        var rows = songs.selectMaps(Wrappers.<Songs>query().eq("id", id).eq(!admin, "published", true));
        if (rows.isEmpty()) throw ApiException.notFound("歌曲不存在或已下架");
        var result = publicSong(rows.getFirst());
        result.put("tags", tags.namesForSong(id));
        return result;
    }

    public Map<String, Object> raw(long id) {
        var rows = songs.selectMaps(Wrappers.<Songs>query().eq("id", id));
        if (rows.isEmpty()) throw ApiException.notFound("歌曲不存在");
        return rows.getFirst();
    }

    public List<Map<String, Object>> hot() {
        try {
            String cached = redis.opsForValue().get("catalog:hot");
            if (cached != null) return json.readValue(cached, new TypeReference<>() {});
        } catch (Exception ignored) { }
        var list = songs.selectMaps(Wrappers.<Songs>query().eq("published", true).orderByDesc("play_count", "id").last("LIMIT 12"))
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
