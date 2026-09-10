package com.omnistudy.agent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
@Component
public class ExplainCurrentTool implements AgentTool {
    public AgentToolSpec spec() { return new AgentToolSpec(AgentToolName.EXPLAIN_CURRENT,
            "读取当前视频时间、字幕和截图上下文，用于解释正在学习的内容", "{\"type\":\"object\",\"properties\":{}}", true, false, 1000); }
    public AgentToolResult execute(AgentExecutionContext context, JsonNode arguments) { return CurrentContextSupport.execute(context); }
}
