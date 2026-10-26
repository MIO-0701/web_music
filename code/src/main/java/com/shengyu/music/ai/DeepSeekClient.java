package com.shengyu.music.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shengyu.music.common.ApiException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekClient {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json;
    private final String endpoint, model, apiKey;
    public DeepSeekClient(ObjectMapper json, @Value("${music.deepseek.base-url}") String baseUrl,
        @Value("${music.deepseek.model}") String model, @Value("${music.deepseek.api-key:}") String apiKey) {
        this.json = json; this.endpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        this.model = model; this.apiKey = apiKey;
    }

    public JsonNode parse(List<Map<String, String>> history) {
        if (apiKey.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "尚未配置 DeepSeek API Key");
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "你是本地音乐曲库的检索条件提取器。参考对话上下文理解追问，但绝不编造歌曲。只返回 JSON 对象，字段为 reply、keyword、genre、language、artist，均为字符串；没有条件时填空字符串。language 只能取 cn、en、jp、fr、ru 或空字符串。reply 是简短中文导语。不要输出曲名或额外字段。"));
            messages.addAll(history);
            String body = json.writeValueAsString(Map.of("model", model, "messages", messages, "response_format", Map.of("type", "json_object"), "stream", false));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("DeepSeek returned " + response.statusCode());
            String content = json.readTree(response.body()).path("choices").path(0).path("message").path("content").asText();
            JsonNode parsed = json.readTree(content);
            if (!parsed.isObject()) throw new IllegalStateException("Invalid model response");
            return parsed;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用，请稍后重试");
        } catch (Exception ex) { throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂时不可用，请稍后重试"); }
    }
}
