package com.omnistudy.agent;
import com.fasterxml.jackson.databind.JsonNode;
public interface AgentTool {
    AgentToolSpec spec();
    AgentToolResult execute(AgentExecutionContext context, JsonNode arguments);
}
