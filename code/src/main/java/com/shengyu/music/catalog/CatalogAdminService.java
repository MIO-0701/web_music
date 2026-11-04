package com.shengyu.music.catalog;

import com.mpatric.mp3agic.Mp3File;
import com.shengyu.music.common.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import com.shengyu.music.persistence.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 曲库管理业务层：校验歌曲数据，向 MinIO 存储媒体，更新 MySQL 歌曲与标签，并清理热门缓存。 */
@Service
public class CatalogAdminService {
    private static final Logger log = LoggerFactory.getLogger(CatalogAdminService.class);
    private final SongsMapper songs;
    private final SongTagsMapper songTags;
    private final SongTagLinksMapper tagLinks;
    private final MediaStorage storage;
    private final PreviewGenerator previews;
    private final CatalogService catalog;
    public CatalogAdminService(SongsMapper songs, SongTagsMapper songTags, SongTagLinksMapper tagLinks, MediaStorage storage, PreviewGenerator previews, CatalogService catalog) {
        this.songs = songs; this.songTags = songTags; this.tagLinks = tagLinks; this.storage = storage; this.previews = previews; this.catalog = catalog;
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
        Songs song = new Songs(); song.title = title.trim(); song.artist = artist.trim(); song.albumName = album == null ? "" : album.trim();
        song.languageCode = language == null ? "other" : language; song.genre = genre == null ? "未分类" : genre.trim();
        song.audioKey = audioKey; song.previewKey = previewKey; song.coverKey = coverKey; song.sourcePath = sourcePath;
        song.durationSeconds = duration; song.vip = vip; song.published = true; songs.insert(song);
        long id = song.id;
        setTags(id, tags == null ? List.of() : tags);
        catalog.invalidateHot();
        log.info("歌曲创建成功，songId={}", id);
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
                try { String previewKey = storage.put(preview, "previews", "mp3"); songs.update(null, Wrappers.<Songs>update().eq("id", id).set("preview_key", previewKey)); }
                finally { Files.deleteIfExists(preview); }
            } catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "生成试听文件失败"); }
            finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (Exception ignored) { } }
        }
        songs.update(null, Wrappers.<Songs>update().eq("id", id).set("title", title.trim()).set("artist", artist.trim())
            .set("album_name", album == null ? "" : album.trim()).set("genre", genre == null ? "未分类" : genre.trim())
            .set("description", description).set("vip", vip).set("published", published));
        setTags(id, tags == null ? List.of() : tags);
        catalog.invalidateHot();
        log.info("歌曲更新成功，songId={}", id);
    }

    /**
     * 设置歌曲上架状态。
     * 处理流程：上架前校验音频时长及会员试听文件，更新 songs.published 并清理热门缓存。
     * @param id 记录 ID
     * @param published 是否上架
     */
    public void publish(long id, boolean published) {
        var row = catalog.raw(id);
        if (published && ((Number) row.get("duration_seconds")).intValue() <= 0)
            throw ApiException.badRequest("音频内容无效，不能上架");
        if (published && Boolean.TRUE.equals(row.get("vip")) && row.get("preview_key") == null)
            throw ApiException.badRequest("会员歌曲必须先生成试听文件");
        songs.update(null, Wrappers.<Songs>update().eq("id", id).set("published", published));
        catalog.invalidateHot();
        log.info("歌曲上架状态已更新，songId={}, published={}", id, published);
    }

    /**
     * 批量上下架、删除或调整会员状态。
     * 处理流程：校验歌曲 ID 和操作类型；逐条更新 songs；删除操作在事务提交后清理 MinIO 对象。
     * @param ids 记录 ID 列表
     * @param action 批量操作类型
     * @return 实际处理的去重歌曲数量
     */
    @Transactional
    public int batch(List<Long> ids, String action) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw ApiException.badRequest("每次须选择 1 到 100 首歌曲");
        var unique = ids.stream().distinct().toList();
        if (!List.of("UNPUBLISH", "DELETE", "SET_VIP", "CLEAR_VIP").contains(action)) throw ApiException.badRequest("批量操作不支持");
        var rows = unique.stream().map(catalog::raw).toList();
        if ("DELETE".equals(action)) {
            var keys = rows.stream().flatMap(row -> java.util.stream.Stream.of(row.get("audio_key"), row.get("preview_key"), row.get("cover_key")))
                .filter(Objects::nonNull).map(String::valueOf).toList();
            for (long id : unique) songs.deleteById(id);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * 数据库删除事务提交后清理对应的 MinIO 媒体对象。
                 */
                @Override public void afterCommit() {
                    for (String key : keys) try { storage.delete(key); }
                    catch (Exception ex) { log.warn("Could not remove media object {} after song deletion", key, ex); }
                }
            });
        } else if ("UNPUBLISH".equals(action)) {
            for (long id : unique) songs.update(null, Wrappers.<Songs>update().eq("id", id).set("published", false));
        } else if ("CLEAR_VIP".equals(action)) {
            for (long id : unique) songs.update(null, Wrappers.<Songs>update().eq("id", id).set("vip", false));
        } else {
            for (var row : rows) {
                long id = ((Number) row.get("id")).longValue();
                if (row.get("preview_key") == null) {
                    Path audio = null;
                    try {
                        audio = Files.createTempFile("shengyu-batch-vip-", ".mp3");
                        try (var input = storage.read((String) row.get("audio_key"), 0, -1)) { Files.copy(input, audio, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                        Path preview = previews.generate(audio);
                        try { songs.update(null, Wrappers.<Songs>update().eq("id", id).set("preview_key", storage.put(preview, "previews", "mp3"))); }
                        finally { Files.deleteIfExists(preview); }
                    } catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "歌曲 " + id + " 生成试听文件失败"); }
                    finally { if (audio != null) try { Files.deleteIfExists(audio); } catch (Exception ignored) { } }
                }
                songs.update(null, Wrappers.<Songs>update().eq("id", id).set("vip", true));
            }
        }
        catalog.invalidateHot();
        log.info("歌曲批量操作完成，action={}, count={}", action, unique.size());
        return unique.size();
    }

    /**
     * 替换歌曲标签。
     * @param songId 歌曲 ID
     * @param tags 歌曲标签
     */
    private void setTags(long songId, List<String> tags) {
        tagLinks.delete(Wrappers.<SongTagLinks>query().eq("song_id", songId));
        for (String raw : tags.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).distinct().limit(12).toList()) {
            if (raw.length() > 60) throw ApiException.badRequest("标签过长");
            songTags.insertIgnore(raw);
            SongTags tag = songTags.selectOne(Wrappers.<SongTags>query().eq("name", raw));
            tagLinks.insertIgnore(songId, tag.id);
        }
    }

    /**
     * 提取文件扩展名。
     * @param path path 参数
     * @return 操作结果
     */
    public static String extension(Path path) {
        String name = path.getFileName().toString(); int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
    /**
     * 校验必填文本及长度。
     * @param value 待设置的新状态或值
     * @param max max 参数
     * @param label label 参数
     */
    private void requireText(String value, int max, String label) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw ApiException.badRequest(label + "不能为空或过长");
    }
}
