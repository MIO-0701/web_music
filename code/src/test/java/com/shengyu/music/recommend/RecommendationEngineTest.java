package com.shengyu.music.recommend;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationEngineTest {
    @Test void tagRulesUseListeningPreferenceAndExcludeKnownSongs() {
        var events = List.of(new RecommendationEngine.Interaction(1, 1, 5));
        var tags = Map.of(1L, Set.of("folk"), 2L, Set.of("folk"), 3L, Set.of("rock"));
        var ranked = RecommendationEngine.byTags(1, Set.of(1L, 2L, 3L), events, tags, Map.of(3L, 100d), 10);
        assertThat(ranked).extracting(RecommendationEngine.Ranked::itemId).containsExactly(2L, 3L);
        assertThat(ranked.getFirst().reason()).contains("标签");
    }

    @Test void itemSimilarityUsesImplicitCoOccurrenceAndExcludesKnownSongs() {
        var events = List.of(
            new RecommendationEngine.Interaction(1, 1, 2),
            new RecommendationEngine.Interaction(2, 1, 1),
            new RecommendationEngine.Interaction(2, 2, 1),
            new RecommendationEngine.Interaction(3, 3, 1));
        var model = RecommendationEngine.buildItemModel(events);
        var ranked = RecommendationEngine.itemCollaborative(1, Set.of(1L, 2L, 3L), events, Map.of(), model, 10);
        assertThat(ranked).extracting(RecommendationEngine.Ranked::itemId).containsExactly(2L);
        assertThat(ranked.getFirst().score()).isPositive();
    }

    @Test void coldStartFallsBackToPopularity() {
        var ranked = RecommendationEngine.itemCollaborative(99, Set.of(1L, 2L), List.of(), Map.of(1L, 2d, 2L, 9d), 10);
        assertThat(ranked).extracting(RecommendationEngine.Ranked::itemId).containsExactly(2L, 1L);
    }
}
