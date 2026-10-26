package com.shengyu.music.catalog;

import com.mpatric.mp3agic.Mp3File;
import com.shengyu.music.common.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogAdminService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final MediaStorage storage;
    private final PreviewGenerator previews;
    private final CatalogService catalog;
    public CatalogAdminService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, MediaStorage storage, PreviewGenerator previews, CatalogService catalog) {
        this.jdbc = jdbc; this.named = named; this.storage = storage; this.previews = previews; this.catalog = catalog;
    }

    @Transactional
    public long add(Path audio, Path cover, String title, String artist, String album, String language,
        String genre, boolean vip, String sourcePath, List<String> tags) {
        requireText(title, 200, "歌名"); requireText(artist, 200, "歌手");
        if (!Files.isRegularFile(audio) || !extension(audio).equals("mp3")) throw ApiException.badRequest("仅支持 MP3 音频");
        if (cover != null && !List.of("jpg", "jpeg", "png", "webp").contains(extension(cover))) throw ApiException.badRequest("封面须为 JPG、PNG 或 WebP");
        int duration;
        try { duration = (int) new Mp3File(audio.toFile()).getLengthInSeconds(); }
        catch (Exception ex) { throw ApiException.badRequest("音频内容不是有效的 MP3"); }
        if (duration <= 0) throw ApiException.badRequest("音频内容不是有效的 MP3");
        String previewKey = null;
        if (vip) {
            Path preview = previews.generate(audio);
            try { previewKey = storage.put(preview, "previews", "mp3"); }
            finally { try { Files.deleteIfExists(preview); } catch (Exception ignored) { } }
        }
        String audioKey = storage.put(audio, "audio", "mp3");
        String coverKey = cover == null ? null : storage.put(cover, "covers", extension(cover));
        var params = new MapSqlParameterSource(Map.of(
            "title", title.trim(), "artist", artist.trim(), "album", album == null ? "" : album.trim(),
            "language", language == null ? "other" : language, "genre", genre == null ? "未分类" : genre.trim(),
            "audio", audioKey, "duration", duration, "vip", vip))
            .addValue("preview", previewKey).addValue("cover", coverKey).addValue("source", sourcePath);
        var keys = new GeneratedKeyHolder();
        named.update("INSERT INTO songs(title,artist,album_name,language_code,genre,audio_key,preview_key,cover_key,source_path,duration_seconds,vip,published) " +
            "VALUES(:title,:artist,:album,:language,:genre,:audio,:preview,:cover,:source,:duration,:vip,TRUE)", params, keys);
        long id = Objects.requireNonNull(keys.getKey()).longValue();
        setTags(id, tags == null ? List.of() : tags);
        catalog.invalidateHot();
        return id;
    }

    @Transactional
    public void edit(long id, String title, String artist, String album, String genre, String description,
        boolean vip, boolean published, List<String> tags) {
        var row = catalog.raw(id);
        requireText(title, 200, "歌名"); requireText(artist, 200, "歌手");
        if ((vip || published) && ((Number) row.get("duration_seconds")).intValue() <= 0)
            throw ApiException.badRequest("音频内容无效，不能上架或设为会员歌曲");
        if (vip && row.get("preview_key") == null) {
            String audioKey = (String) row.get("audio_key");
            Path temp = null;
            try {
                temp = Files.createTempFile("shengyu-vip-", ".mp3");
                try (var input = storage.read(audioKey, 0, -1)) { Files.copy(input, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                Path preview = previews.generate(temp);
                try { String previewKey = storage.put(preview, "previews", "mp3"); jdbc.update("UPDATE songs SET preview_key=? WHERE id=?", previewKey, id); }
                finally { Files.deleteIfExists(preview); }
            } catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "生成试听文件失败"); }
            finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) { } }
        }
        jdbc.update("UPDATE songs SET title=?,artist=?,album_name=?,genre=?,description=?,vip=?,published=? WHERE id=?",
            title.trim(), artist.trim(), album == null ? "" : album.trim(), genre == null ? "未分类" : genre.trim(), description, vip, published, id);
        setTags(id, tags == null ? List.of() : tags);
        catalog.invalidateHot();
    }

    public void publish(long id, boolean published) {
        var row = catalog.raw(id);
        if (published && ((Number) row.get("duration_seconds")).intValue() <= 0)
            throw ApiException.badRequest("音频内容无效，不能上架");
        if (published && Boolean.TRUE.equals(row.get("vip")) && row.get("preview_key") == null)
            throw ApiException.badRequest("会员歌曲必须先生成试听文件");
        jdbc.update("UPDATE songs SET published=? WHERE id=?", published, id);
        catalog.invalidateHot();
    }

    private void setTags(long songId, List<String> tags) {
        jdbc.update("DELETE FROM song_tag_links WHERE song_id=?", songId);
        for (String raw : tags.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().limit(12).toList()) {
            if (raw.length() > 60) throw ApiException.badRequest("标签过长");
            jdbc.update("INSERT IGNORE INTO song_tags(name) VALUES(?)", raw);
            Long tagId = jdbc.queryForObject("SELECT id FROM song_tags WHERE name=?", Long.class, raw);
            jdbc.update("INSERT IGNORE INTO song_tag_links(song_id,tag_id) VALUES(?,?)", songId, tagId);
        }
    }

    public static String extension(Path path) {
        String name = path.getFileName().toString(); int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
    private void requireText(String value, int max, String label) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw ApiException.badRequest(label + "不能为空或过长");
    }
}
