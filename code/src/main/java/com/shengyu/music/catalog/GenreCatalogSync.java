package com.shengyu.music.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Applies reviewed offline genre metadata after the optional local music import. */
@Component
public class GenreCatalogSync {
    private static final Logger log = LoggerFactory.getLogger(GenreCatalogSync.class);
    private final Path catalogPath;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final CatalogService catalog;

    public GenreCatalogSync(@Value("${music.genre-catalog-path:../data/song-genres.json}") String path,
        ObjectMapper json, JdbcTemplate jdbc, CatalogService catalog) {
        this.catalogPath = Path.of(path).toAbsolutePath().normalize();
        this.json = json; this.jdbc = jdbc; this.catalog = catalog;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sync() throws Exception {
        if (!Files.isRegularFile(catalogPath)) {
            log.warn("Genre catalog not found: {}", catalogPath);
            return;
        }
        JsonNode songs = json.readTree(Files.readString(catalogPath)).path("songs");
        if (!songs.isArray()) throw new IllegalStateException("Genre catalog songs must be an array");
        int updated = 0;
        for (JsonNode song : songs) {
            if (!"matched".equals(song.path("status").asText()) || song.path("genre").asText().isBlank()) continue;
            updated += jdbc.update("UPDATE songs SET genre=? WHERE source_path=? AND genre='未分类'",
                song.path("genre").asText(), song.path("sourcePath").asText());
        }
        if (updated > 0) catalog.invalidateHot();
        log.info("Genre catalog checked {} entries, updated {} songs", songs.size(), updated);
    }
}
