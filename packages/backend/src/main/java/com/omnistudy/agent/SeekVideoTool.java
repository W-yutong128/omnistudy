package com.omnistudy.agent;
import com.fasterxml.jackson.databind.JsonNode;
import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
@Component @RequiredArgsConstructor
public class SeekVideoTool implements AgentTool {
    private final RagService ragService;
    public AgentToolSpec spec() { return new AgentToolSpec(AgentToolName.SEEK_VIDEO,
            "在当前课程摘要中定位概念并返回视频时间", "{\"type\":\"object\",\"required\":[\"query\"],\"properties\":{\"query\":{\"type\":\"string\"}}}", true, false, 3000); }
    public AgentToolResult execute(AgentExecutionContext context, JsonNode arguments) {
        String query = arguments.path("query").asText(context.request().message()).trim();
        var hit = ragService.search(context.userId(), context.session().getId(), query, 8).stream().filter(item -> item.startTime() != null).findFirst().orElse(null);
        if (hit == null) return AgentToolResult.of("当前课程索引中没有找到“" + query + "”对应的时间点。");
        var source = new AgentChatResponse.Source(hit.title(), hit.content(), hit.part(), hit.startTime());
        return new AgentToolResult("已定位到 " + hit.title() + "。", List.of(source), Math.max(0, hit.startTime()), null);
    }
}
