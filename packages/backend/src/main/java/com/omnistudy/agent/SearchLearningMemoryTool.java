package com.omnistudy.agent;
import com.fasterxml.jackson.databind.JsonNode;
import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class SearchLearningMemoryTool implements AgentTool {
    private final RagService ragService;
    public AgentToolSpec spec() { return new AgentToolSpec(AgentToolName.SEARCH_LEARNING_MEMORY,
            "混合检索用户笔记和课程摘要，返回可引用来源", "{\"type\":\"object\",\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"}}}", true, false, 3000); }
    public AgentToolResult execute(AgentExecutionContext context, JsonNode arguments) {
        String query = arguments.path("query").asText(context.request().message()).trim();
        var hits = ragService.search(context.userId(), null, query, 5);
        var sources = hits.stream().map(hit -> new AgentChatResponse.Source(hit.title(), hit.content(), hit.part(), hit.startTime())).toList();
        if (hits.isEmpty()) return new AgentToolResult("没有检索到匹配的学习记录。", sources, null, null);
        StringBuilder observation = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) observation.append("[").append(i + 1).append("] ").append(hits.get(i).title()).append("\n").append(hits.get(i).content()).append("\n\n");
        return new AgentToolResult(observation.toString(), sources, null, null);
    }
}
