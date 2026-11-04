package com.shengyu.music.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import com.shengyu.music.persistence.RecommendationExperimentsMapper;

class ExperimentDatasetTest {
    @Test void officialDatasetProducesReproducibleBoundedMetrics() {
        Path dataset = Path.of("../data/hetrec2011-lastfm-2k.zip").toAbsolutePath().normalize();
        assumeTrue(Files.isRegularFile(dataset), "Official dataset is optional in fresh checkouts");
        RecommendationExperimentsMapper experiments = mock(RecommendationExperimentsMapper.class);
        var service = new ExperimentService(dataset.toString(), experiments, new ObjectMapper());
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
        verify(experiments, times(4)).insert(any());
        System.out.println("Official Last.fm experiment: " + first);
    }
}
