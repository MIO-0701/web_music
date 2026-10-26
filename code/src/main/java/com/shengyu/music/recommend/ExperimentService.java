package com.shengyu.music.recommend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shengyu.music.common.ApiException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExperimentService {
    private static final int MAX_ARTISTS = 800;
    private static final int MAX_USERS = 150;
    private static final int MAX_ITEMS_PER_USER = 60;
    private static final int SEED = 20261023;
    private final Path dataset;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ExperimentService(@Value("${music.experiment.dataset-path:../data/hetrec2011-lastfm-2k.zip}") String datasetPath,
        JdbcTemplate jdbc, ObjectMapper json) { this.dataset = Path.of(datasetPath).toAbsolutePath().normalize(); this.jdbc = jdbc; this.json = json; }

    public List<Map<String, Object>> evaluate() {
        if (!Files.isRegularFile(dataset)) throw new ApiException(HttpStatus.BAD_REQUEST, "找不到 Last.fm 数据集 ZIP，请设置 MUSIC_EXPERIMENT_DATASET_PATH");
        try (ZipFile zip = new ZipFile(dataset.toFile())) {
            Map<Long, Map<Long, Double>> all = parseListens(zip);
            Map<Long, Set<String>> tags = parseTags(zip);
            Map<Long, Integer> frequencies = new HashMap<>();
            all.values().forEach(items -> items.keySet().forEach(item -> frequencies.merge(item, 1, Integer::sum)));
            Set<Long> candidates = frequencies.entrySet().stream().sorted(Map.Entry.<Long, Integer>comparingByValue().reversed().thenComparingLong(Map.Entry::getKey))
                .limit(MAX_ARTISTS).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
            List<Long> users = all.keySet().stream().sorted().filter(user -> all.get(user).keySet().stream().filter(candidates::contains).count() >= 5).limit(MAX_USERS).toList();
            if (users.isEmpty()) throw ApiException.badRequest("数据集中没有足够的有效用户");
            List<RecommendationEngine.Interaction> training = new ArrayList<>();
            Map<Long, Set<Long>> heldout = new HashMap<>();
            Map<Long, Double> popularity = new HashMap<>();
            for (long user : users) {
                List<Long> items = all.get(user).entrySet().stream().filter(entry -> candidates.contains(entry.getKey()))
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed().thenComparingLong(Map.Entry::getKey))
                    .limit(MAX_ITEMS_PER_USER).map(Map.Entry::getKey)
                    .sorted(Comparator.<Long>comparingLong(item -> stableHash(user, item)).thenComparingLong(Long::longValue)).toList();
                int testCount = Math.max(1, items.size() / 5);
                heldout.put(user, new HashSet<>(items.subList(0, testCount)));
                for (long item : items.subList(testCount, items.size())) {
                    double weight = Math.log1p(all.get(user).get(item));
                    training.add(new RecommendationEngine.Interaction(user, item, weight));
                    popularity.merge(item, 1d, Double::sum);
                }
            }
            var model = RecommendationEngine.buildItemModel(training);
            List<Map<String, Object>> result = new ArrayList<>();
            result.add(measure("TAG", users, heldout, candidates, user -> RecommendationEngine.byTags(user, candidates, training, tags, popularity, 10)));
            result.add(measure("ITEM_CF", users, heldout, candidates, user -> RecommendationEngine.itemCollaborative(user, candidates, training, popularity, model, 10)));
            for (var row : result) jdbc.update("INSERT INTO recommendation_experiments(algorithm,dataset,precision_at_10,recall_at_10,coverage,evaluated_users,details) VALUES(?,?,?,?,?,?,?)",
                row.get("algorithm"), row.get("dataset"), row.get("precisionAt10"), row.get("recallAt10"), row.get("coverage"), row.get("evaluatedUsers"), json.writeValueAsString(Map.of("seed", SEED, "maxArtists", MAX_ARTISTS, "maxUsers", MAX_USERS, "maxItemsPerUser", MAX_ITEMS_PER_USER, "holdout", "20% per user, minimum 1", "itemLevel", "artist")));
            return result;
        } catch (ApiException ex) { throw ex; }
        catch (Exception ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "数据集读取或实验计算失败：" + ex.getMessage()); }
    }

    private Map<String, Object> measure(String algorithm, List<Long> users, Map<Long, Set<Long>> heldout, Set<Long> candidates,
        java.util.function.LongFunction<List<RecommendationEngine.Ranked>> ranker) {
        double precision = 0, recall = 0;
        Set<Long> recommended = new HashSet<>();
        for (long user : users) {
            List<RecommendationEngine.Ranked> top = ranker.apply(user);
            long hits = top.stream().filter(item -> heldout.get(user).contains(item.itemId())).count();
            precision += hits / 10d;
            recall += hits / (double) heldout.get(user).size();
            top.forEach(item -> recommended.add(item.itemId()));
        }
        return Map.of("algorithm", algorithm, "dataset", "HetRec 2011 Last.fm 2K (artist subset)",
            "precisionAt10", precision / users.size(), "recallAt10", recall / users.size(),
            "coverage", recommended.size() / (double) candidates.size(), "evaluatedUsers", users.size());
    }

    private Map<Long, Map<Long, Double>> parseListens(ZipFile zip) throws Exception {
        Map<Long, Map<Long, Double>> data = new HashMap<>();
        try (BufferedReader reader = reader(zip, "user_artists.dat")) {
            String line; reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\\t");
                if (columns.length < 3) continue;
                long user = Long.parseLong(columns[0]), artist = Long.parseLong(columns[1]);
                data.computeIfAbsent(user, ignored -> new HashMap<>()).put(artist, Double.parseDouble(columns[2]));
            }
        }
        return data;
    }
    private Map<Long, Set<String>> parseTags(ZipFile zip) throws Exception {
        Map<Long, Set<String>> tags = new HashMap<>();
        try (BufferedReader reader = reader(zip, "user_taggedartists.dat")) {
            String line; reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\\t");
                if (columns.length < 3) continue;
                tags.computeIfAbsent(Long.parseLong(columns[1]), ignored -> new HashSet<>()).add("tag:" + columns[2]);
            }
        }
        return tags;
    }
    private BufferedReader reader(ZipFile zip, String suffix) throws Exception {
        ZipEntry entry = zip.stream().filter(item -> item.getName().endsWith(suffix)).findFirst().orElseThrow(() -> new IllegalArgumentException("ZIP 缺少 " + suffix));
        return new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
    }
    private long stableHash(long user, long item) {
        long x = user * 0x9e3779b97f4a7c15L ^ item * 0xbf58476d1ce4e5b9L ^ SEED;
        x ^= x >>> 30; x *= 0xbf58476d1ce4e5b9L; x ^= x >>> 27; x *= 0x94d049bb133111ebL; return x ^ (x >>> 31);
    }
}
