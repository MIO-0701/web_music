package com.shengyu.music.catalog;

import com.shengyu.music.common.ApiException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import com.shengyu.music.persistence.Songs;
import com.shengyu.music.persistence.SongsMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 批量导入业务层：校验 ZIP 和 CSV，安全解压、逐行导入歌曲，并汇总成功、跳过和失败数量。 */
@Service
public class BatchSongImportService {
    private static final Logger log = LoggerFactory.getLogger(BatchSongImportService.class);
    public static final List<String> GENRES = List.of("pop", "rock", "alternative", "rnb-soul", "hip-hop", "electronic", "dance", "jazz", "classical", "folk", "singer-songwriter", "soundtrack", "anime", "country", "metal", "blues", "reggae", "latin", "instrumental", "new-age");
    private static final List<String> COLUMNS = List.of("title", "artist", "album", "audio_file", "cover_file", "language", "genre", "vip");
    private final CatalogAdminService admin;
    private final SongsMapper songsMapper;
    public BatchSongImportService(CatalogAdminService admin, SongsMapper songsMapper) { this.admin = admin; this.songsMapper = songsMapper; }

    /**
     * 导入 ZIP 音频、封面和 CSV 元数据。
     * 处理流程：校验文件、限制解压路径与体积；逐行解析 CSV，用音频摘要跳过已有歌曲，调用曲库管理服务导入并汇总错误。
     * @param audioZip 音频 ZIP 文件
     * @param coverZip 封面 ZIP 文件
     * @param csv 歌曲元数据 CSV 文件
     * @return 包含总数、导入数、跳过数、失败数及错误明细的结果
     */
    public Map<String, Object> upload(MultipartFile audioZip, MultipartFile coverZip, MultipartFile csv) throws Exception {
        checkFile(audioZip, ".zip", 400_000_000L); checkFile(coverZip, ".zip", 400_000_000L); checkFile(csv, ".csv", 1_000_000L);
        Path root = Files.createTempDirectory("shengyu-batch-");
        try {
            Map<String, Path> audio = unzip(audioZip, root.resolve("audio"), true);
            Map<String, Path> covers = unzip(coverZip, root.resolve("covers"), false);
            List<Row> rows = parseCsv(csv, audio, covers);
            List<Map<String, Object>> failures = new ArrayList<>();
            List<Long> created = new ArrayList<>();
            int skipped = 0;
            for (Row row : rows) {
                try {
                    String source = "batch/sha256/" + sha256(audio.get(row.audioFile));
                    if (songsMapper.selectCount(Wrappers.<Songs>query().eq("source_path", source)) > 0) { skipped++; continue; }
                    created.add(admin.add(audio.get(row.audioFile), covers.get(row.coverFile), row.title, row.artist, row.album,
                        row.language, row.genre, row.vip, source, List.of(row.language, row.genre)));
                } catch (Exception ex) {
                    failures.add(Map.of("row", row.number, "file", row.audioFile, "error", ex.getMessage() == null ? "导入失败" : ex.getMessage()));
                    log.warn("批量歌曲导入失败，row={}", row.number, ex);
                }
            }
            log.info("批量歌曲导入完成，total={}, imported={}, skipped={}, failed={}", rows.size(), created.size(), skipped, failures.size());
            return Map.of("total", rows.size(), "imported", created.size(), "skipped", skipped, "failed", failures.size(), "ids", created, "errors", failures);
        } finally {
            try (var paths = Files.walk(root)) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } }); }
        }
    }

    /**
     * 校验上传文件格式与大小。
     * @param file file 参数
     * @param extension extension 参数
     * @param max max 参数
     */
    private void checkFile(MultipartFile file, String extension, long max) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(extension) || file.getSize() > max)
            throw ApiException.badRequest(extension + " 文件缺失、格式错误或过大");
    }

    /**
     * 安全解压上传文件。
     * @param archive archive 参数
     * @param destination destination 参数
     * @param music music 参数
     * @return 操作结果
     */
    private Map<String, Path> unzip(MultipartFile archive, Path destination, boolean music) throws Exception {
        Files.createDirectories(destination);
        Map<String, Path> found = new HashMap<>();
        long total = 0; int count = 0;
        try (ZipInputStream zip = new ZipInputStream(archive.getInputStream(), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains("//") || name.contains(":") || name.endsWith("/")) throw ApiException.badRequest("ZIP 内路径不安全");
                String lower = name.toLowerCase();
                if (music ? !lower.endsWith(".mp3") : !(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")))
                    throw ApiException.badRequest("ZIP 包含不支持的文件：" + name);
                if (++count > 500 || found.containsKey(name)) throw ApiException.badRequest("ZIP 文件数超过 500 或有重复路径");
                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination)) throw ApiException.badRequest("ZIP 内路径不安全");
                Files.createDirectories(target.getParent());
                long single = 0, limit = music ? 50_000_000L : 10_000_000L;
                try (var output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192]; int length;
                    while ((length = zip.read(buffer)) != -1) {
                        single += length; total += length;
                        if (single > limit || total > 1_000_000_000L) throw ApiException.badRequest("ZIP 解压后文件过大");
                        output.write(buffer, 0, length);
                    }
                }
                found.put(name, target);
                zip.closeEntry();
            }
        }
        if (found.isEmpty()) throw ApiException.badRequest("ZIP 中没有可导入的文件");
        return found;
    }

    /**
     * 解析并校验歌曲元数据。
     * @param csv 歌曲元数据 CSV 文件
     * @param String String 参数
     * @param audio 音频文件路径
     * @param String String 参数
     * @param covers covers 参数
     * @return 操作结果
     */
    private List<Row> parseCsv(MultipartFile csv, Map<String, Path> audio, Map<String, Path> covers) throws Exception {
        List<Row> rows = new ArrayList<>(); Set<String> used = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !parseLine(header.replaceFirst("^\\uFEFF", "")).equals(COLUMNS)) throw ApiException.badRequest("CSV 表头应为：" + String.join(",", COLUMNS));
            String line; int number = 1;
            while ((line = reader.readLine()) != null) {
                number++; if (line.isBlank()) continue;
                List<String> values = parseLine(line);
                if (values.size() != COLUMNS.size()) throw ApiException.badRequest("CSV 第 " + number + " 行列数不正确");
                Map<String, String> fields = new LinkedHashMap<>();
                for (int i = 0; i < COLUMNS.size(); i++) fields.put(COLUMNS.get(i), values.get(i).trim());
                String file = fields.get("audio_file"), cover = fields.get("cover_file"), language = fields.get("language"), genre = fields.get("genre"), vip = fields.get("vip");
                if (fields.get("title").isBlank() || fields.get("artist").isBlank() || fields.get("title").length() > 200 || fields.get("artist").length() > 200)
                    throw ApiException.badRequest("CSV 第 " + number + " 行歌名或歌手无效");
                if (!audio.containsKey(file) || !covers.containsKey(cover) || !used.add(file)) throw ApiException.badRequest("CSV 第 " + number + " 行文件路径不存在或重复");
                if (!List.of("cn", "en", "jp", "fr", "ru").contains(language) || !GENRES.contains(genre) || !List.of("true", "false").contains(vip.toLowerCase()))
                    throw ApiException.badRequest("CSV 第 " + number + " 行语种、分类或 VIP 值无效");
                rows.add(new Row(number, fields.get("title"), fields.get("artist"), fields.get("album"), file, cover, language, genre, Boolean.parseBoolean(vip)));
                if (rows.size() > 100) throw ApiException.badRequest("单次最多上传 100 首歌曲");
            }
        }
        if (rows.isEmpty()) throw ApiException.badRequest("CSV 中没有歌曲");
        return rows;
    }

    /**
     * 解析一行 CSV 数据。
     * @param line line 参数
     * @return 操作结果
     */
    private List<String> parseLine(String line) {
        List<String> result = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = !quoted; }
            else if (c == ',' && !quoted) { result.add(cell.toString()); cell.setLength(0); }
            else cell.append(c);
        }
        if (quoted) throw ApiException.badRequest("CSV 引号未闭合");
        result.add(cell.toString()); return result;
    }

    /**
     * 计算文件 SHA-256 摘要。
     * @param file file 参数
     * @return 操作结果
     */
    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) { byte[] bytes = new byte[8192]; int n; while ((n = input.read(bytes)) != -1) digest.update(bytes, 0, n); }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    private record Row(int number, String title, String artist, String album, String audioFile, String coverFile,
        String language, String genre, boolean vip) {}
}
