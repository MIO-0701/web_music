package com.shengyu.music.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ExperimentDatasetTest {
    @Test void officialDatasetProducesReproducibleBoundedMetrics() {
        Path dataset = Path.of("../data/hetrec2011-lastfm-2k.zip").toAbsolutePath().normalize();
        assumeTrue(Files.isRegularFile(dataset), "Official dataset is optional in fresh checkouts");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var service = new ExperimentService(dataset.toString(), jdbc, new ObjectMapper());
        var first = service.evaluate();
        var second = service.evaluate();
        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(2);
        first.forEach(row -> {
            assertThat((double) row.get("precisionAt10")).isBetween(0d, 1d);
            assertThat((double) row.get("recallAt10")).isBetween(0d, 1d);
            assertThat((double) row.get("coverage")).isBetween(0d, 1d);
            assertThat((int) row.get("evaluatedUsers")).isPositive();
        });
        verify(jdbc, times(4)).update(startsWith("INSERT INTO recommendation_experiments"), any(), any(), any(), any(), any(), any(), any());
        System.out.println("Official Last.fm experiment: " + first);
    }
}
