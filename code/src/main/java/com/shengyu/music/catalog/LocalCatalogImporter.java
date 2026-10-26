package com.shengyu.music.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LocalCatalogImporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalCatalogImporter.class);
    private final boolean enabled;
    private final Path musicRoot, imageRoot;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final CatalogAdminService admin;
    public LocalCatalogImporter(@Value("${music.import.enabled:false}") boolean enabled,
        @Value("${music.import.music-dir:../music}") String musicDir,
        @Value("${music.import.image-dir:../img}") String imageDir,
        ObjectMapper json, JdbcTemplate jdbc, CatalogAdminService admin) {
        this.enabled = enabled; this.musicRoot = Path.of(musicDir).toAbsolutePath().normalize();
        this.imageRoot = Path.of(imageDir).toAbsolutePath().normalize();
        this.json = json; this.jdbc = jdbc; this.admin = admin;
    }

    @Override public void run(ApplicationArguments args) throws Exception {
        if (!enabled) return;
        Path manifest = imageRoot.resolve("_cover-manifest.json");
        JsonNode entries = json.readTree(Files.readString(manifest));
        int imported = 0, skipped = 0, failed = 0;
        List<String> failures = new ArrayList<>();
        for (JsonNode entry : entries) {
            String category = entry.path("category").asText();
            String file = entry.path("file").asText();
            String title = entry.path("title").asText();
            String artist = entry.path("artist").asText();
            String source = category + "/" + file;
            if (!"matched".equals(entry.path("status").asText()) || title.isBlank() || "null".equalsIgnoreCase(title) || artist.isBlank()) { skipped++; continue; }
            if (jdbc.queryForObject("SELECT COUNT(*) FROM songs WHERE source_path=?", Long.class, source) > 0) { skipped++; continue; }
            Path audio = musicRoot.resolve(source).normalize();
            if (!audio.startsWith(musicRoot) || !Files.isRegularFile(audio)) { failures.add(source + " (音频缺失)"); failed++; continue; }
            String image = entry.path("image").asText();
            Path cover = image.isBlank() ? null : imageRoot.resolve(image).normalize();
            if (cover != null && (!cover.startsWith(imageRoot) || !Files.isRegularFile(cover))) cover = null;
            String album = entry.path("candidates").path(0).path("album").asText("");
            try {
                admin.add(audio, cover, title, artist, album, category, "未分类", false, source, List.of(category));
                imported++;
                if (imported % 50 == 0) log.info("Imported {} songs", imported);
            } catch (Exception ex) { failures.add(source + " (" + ex.getMessage() + ")"); failed++; }
        }
        log.info("Local catalog import finished: imported={}, skipped={}, failed={}", imported, skipped, failed);
        failures.forEach(item -> log.warn("Import failure: {}", item));
    }
}
