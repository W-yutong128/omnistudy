package com.omnistudy.agent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
@Component
public class RespondTool implements AgentTool {
    public AgentToolSpec spec() { return new AgentToolSpec(AgentToolName.RESPOND,
            "普通学习对话，不读取额外数据", "{\"type\":\"object\",\"properties\":{}}", true, false, 500); }
    public AgentToolResult execute(AgentExecutionContext context, JsonNode arguments) { return AgentToolResult.of("无需额外工具。"); }
}
