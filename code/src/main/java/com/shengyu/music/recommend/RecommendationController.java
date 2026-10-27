package com.shengyu.music.recommend;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public class RecommendationController {
    private final RecommendationService recommendations;
    private final JdbcTemplate jdbc;
    private final ExperimentService experiment;
    public RecommendationController(RecommendationService recommendations, JdbcTemplate jdbc, ExperimentService experiment) { this.recommendations = recommendations; this.jdbc = jdbc; this.experiment = experiment; }
    @GetMapping("/api/me/recommendations") public List<Map<String, Object>> mine(@RequestParam(defaultValue = "TAG") String algorithm) {
        return recommendations.mine(algorithm.toUpperCase());
    }
    @GetMapping("/api/me/recommendations/daily") public List<Map<String, Object>> daily() {
        return recommendations.dailyForCurrentUser();
    }
    @PostMapping("/api/admin/recommendations/rebuild") public Map<String, Object> rebuild() {
        return Map.of("usersProcessed", recommendations.rebuild());
    }
    @GetMapping("/api/admin/recommendations/experiments") public List<Map<String, Object>> experiments() {
        return jdbc.queryForList("SELECT id,algorithm,dataset,precision_at_10,recall_at_10,coverage,evaluated_users,details,created_at FROM recommendation_experiments ORDER BY id DESC LIMIT 20");
    }
    @PostMapping("/api/admin/recommendations/evaluate") public List<Map<String, Object>> evaluate() { return experiment.evaluate(); }
}
