package com.shengyu.music.recommend;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** 推荐接口层：提供个人推荐、管理端重建和离线实验查询入口。 */
@RestController
public class RecommendationController {
    private final RecommendationService recommendations;
    private final ExperimentService experiment;
    public RecommendationController(RecommendationService recommendations, ExperimentService experiment) { this.recommendations = recommendations; this.experiment = experiment; }
    /**
     * 接收查询我的推荐请求，调用对应业务服务并返回接口响应。
     * @param algorithm 推荐算法标识
     * @return 接口响应
     */
    @GetMapping("/api/me/recommendations") public List<Map<String, Object>> mine(@RequestParam(defaultValue = "TAG") String algorithm) {
        return recommendations.mine(algorithm.toUpperCase());
    }
    /**
     * 接收定时重建推荐请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @GetMapping("/api/me/recommendations/daily") public List<Map<String, Object>> daily() {
        return recommendations.dailyForCurrentUser();
    }
    /**
     * 接收手动重建推荐请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @PostMapping("/api/admin/recommendations/rebuild") public Map<String, Object> rebuild() {
        return Map.of("usersProcessed", recommendations.rebuild());
    }
    /**
     * 接收查询离线实验结果请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @GetMapping("/api/admin/recommendations/experiments") public List<Map<String, Object>> experiments() {
        return experiment.experiments();
    }
    /**
     * 接收运行离线推荐评估请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @PostMapping("/api/admin/recommendations/evaluate") public List<Map<String, Object>> evaluate() { return experiment.evaluate(); }
}
