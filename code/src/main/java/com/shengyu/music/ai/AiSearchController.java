package com.shengyu.music.ai;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/ai")
public class AiSearchController {
    private final AiSearchService search;
    public AiSearchController(AiSearchService search) { this.search = search; }
    public record ChatRequest(Long conversationId, String message) {}
    @GetMapping("/conversations") public List<Map<String, Object>> conversations() { return search.conversations(); }
    @GetMapping("/conversations/{id}/messages") public List<Map<String, Object>> messages(@PathVariable long id) { return search.messages(id); }
    @PostMapping("/chat") public Map<String, Object> chat(@RequestBody ChatRequest body) { return search.chat(body.conversationId(), body.message()); }
}
