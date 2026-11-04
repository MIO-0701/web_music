package com.shengyu.music.ai;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/** AI 查歌接口层：提供对话列表、消息历史和自然语言查歌请求。 */
@RestController
@RequestMapping("/api/me/ai")
public class AiSearchController {
    private final AiSearchService search;
    public AiSearchController(AiSearchService search) { this.search = search; }
    /** AI 对话请求，包含可选会话 ID 与查歌描述。 */
    public record ChatRequest(Long conversationId, String message) {}
    /**
     * 接收查询 AI 会话列表请求，调用对应业务服务并返回接口响应。
     * @return 接口响应
     */
    @GetMapping("/conversations") public List<Map<String, Object>> conversations() { return search.conversations(); }
    /**
     * 接收查询 AI 会话消息请求，调用对应业务服务并返回接口响应。
     * @param id 目标记录 ID
     * @return 接口响应
     */
    @GetMapping("/conversations/{id}/messages") public List<Map<String, Object>> messages(@PathVariable long id) { return search.messages(id); }
    /**
     * 接收发送 AI 查歌消息请求，调用对应业务服务并返回接口响应。
     * @param body 已解析的请求内容
     * @return 接口响应
     */
    @PostMapping("/chat") public Map<String, Object> chat(@RequestBody ChatRequest body) { return search.chat(body.conversationId(), body.message()); }
}
