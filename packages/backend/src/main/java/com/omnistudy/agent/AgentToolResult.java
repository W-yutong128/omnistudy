package com.omnistudy.agent;
import com.omnistudy.model.dto.AgentChatResponse;
import java.util.List;
public record AgentToolResult(String observation, List<AgentChatResponse.Source> sources, Double seekTo,
                              AgentChatResponse.IdeHandoff ideHandoff) {
    public static AgentToolResult of(String observation) { return new AgentToolResult(observation, List.of(), null, null); }
}
