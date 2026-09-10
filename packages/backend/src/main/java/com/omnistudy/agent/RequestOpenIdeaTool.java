package com.omnistudy.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnistudy.model.dto.AgentChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RequestOpenIdeaTool implements AgentTool {
    @Override
    public AgentToolSpec spec() {
        return new AgentToolSpec(AgentToolName.REQUEST_OPEN_IDEA,
                "当课程涉及写代码时，建议用户在本机 IDEA 中继续；只生成待确认动作，不打开应用",
                "{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\"},\"suggestedFile\":{\"type\":\"string\"}}}",
                false, true, 1000);
    }

    @Override
    public AgentToolResult execute(AgentExecutionContext context, JsonNode arguments) {
        String reason = arguments.path("reason").asText("这个问题适合进入 IDE 动手练习");
        String file = arguments.path("suggestedFile").asText(null);
        var handoff = new AgentChatResponse.IdeHandoff(reason, file);
        return new AgentToolResult("已生成 IDEA 打开建议，等待用户明确确认。", List.of(), null, handoff);
    }
}
