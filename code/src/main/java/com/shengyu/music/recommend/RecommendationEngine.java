package com.shengyu.music.recommend;

import java.util.*;
import java.util.stream.Collectors;

/** Pure ranking functions shared by production recommendations and offline evaluation. */
public final class RecommendationEngine {
    private RecommendationEngine() {}
    public record Interaction(long userId, long itemId, double weight) {}
    public record Ranked(long itemId, double score, String reason) {}
    public record ItemModel(Map<Long, Map<Long, Double>> dot, Map<Long, Double> norm) {}

    public static List<Ranked> byTags(long userId, Set<Long> candidates, List<Interaction> interactions,
        Map<Long, Set<String>> tags, Map<Long, Double> popularity, int limit) {
        Map<Long, Double> seen = interactions.stream().filter(i -> i.userId() == userId)
            .collect(Collectors.toMap(Interaction::itemId, Interaction::weight, Double::sum));
        Map<String, Double> preference = new HashMap<>();
        seen.forEach((item, weight) -> tags.getOrDefault(item, Set.of()).forEach(tag -> preference.merge(tag, weight, Double::sum)));
        double maxPopularity = popularity.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        return candidates.stream().filter(item -> !seen.containsKey(item)).map(item -> {
            double match = tags.getOrDefault(item, Set.of()).stream().mapToDouble(tag -> preference.getOrDefault(tag, 0d)).sum();
            double score = match + 0.05 * popularity.getOrDefault(item, 0d) / Math.max(1, maxPopularity);
            return new Ranked(item, score, match > 0 ? "符合你常听的歌曲标签" : "当前热门歌曲");
        }).sorted(Comparator.comparingDouble(Ranked::score).reversed().thenComparingLong(Ranked::itemId)).limit(limit).toList();
    }

    public static List<Ranked> itemCollaborative(long userId, Set<Long> candidates, List<Interaction> interactions,
        Map<Long, Double> popularity, int limit) {
        return itemCollaborative(userId, candidates, interactions, popularity, buildItemModel(interactions), limit);
    }

    public static ItemModel buildItemModel(List<Interaction> interactions) {
        Map<Long, Map<Long, Double>> byUser = new HashMap<>();
        for (Interaction i : interactions) byUser.computeIfAbsent(i.userId(), ignored -> new HashMap<>()).merge(i.itemId(), i.weight(), Double::sum);
        Map<Long, Double> norm = new HashMap<>();
        Map<Long, Map<Long, Double>> dot = new HashMap<>();
        byUser.values().forEach(items -> {
            items.forEach((item, weight) -> norm.merge(item, weight * weight, Double::sum));
            List<Map.Entry<Long, Double>> entries = new ArrayList<>(items.entrySet());
            for (int a = 0; a < entries.size(); a++) for (int b = a + 1; b < entries.size(); b++) {
                var left = entries.get(a); var right = entries.get(b);
                double product = left.getValue() * right.getValue();
                dot.computeIfAbsent(left.getKey(), ignored -> new HashMap<>()).merge(right.getKey(), product, Double::sum);
                dot.computeIfAbsent(right.getKey(), ignored -> new HashMap<>()).merge(left.getKey(), product, Double::sum);
            }
        });
        return new ItemModel(dot, norm);
    }

    public static List<Ranked> itemCollaborative(long userId, Set<Long> candidates, List<Interaction> interactions,
        Map<Long, Double> popularity, ItemModel model, int limit) {
        Map<Long, Double> target = interactions.stream().filter(i -> i.userId() == userId)
            .collect(Collectors.toMap(Interaction::itemId, Interaction::weight, Double::sum));
        Map<Long, Double> scores = new HashMap<>();
        target.forEach((known, weight) -> model.dot().getOrDefault(known, Map.of()).forEach((candidate, product) -> {
            if (candidates.contains(candidate) && !target.containsKey(candidate)) {
                double similarity = product / Math.sqrt(model.norm().getOrDefault(known, 1d) * model.norm().getOrDefault(candidate, 1d));
                scores.merge(candidate, similarity * weight, Double::sum);
            }
        }));
        if (scores.isEmpty()) {
            double maxPopularity = popularity.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
            candidates.stream().filter(item -> !target.containsKey(item)).forEach(item -> scores.put(item, popularity.getOrDefault(item, 0d) / Math.max(1, maxPopularity)));
        }
        return scores.entrySet().stream().map(e -> new Ranked(e.getKey(), e.getValue(), target.isEmpty() ? "新用户热门推荐" : "与你常听的歌曲相似"))
            .sorted(Comparator.comparingDouble(Ranked::score).reversed().thenComparingLong(Ranked::itemId)).limit(limit).toList();
    }
}
