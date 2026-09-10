package com.omnistudy.agent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
@Component
public class CreatePracticeTool implements AgentTool {
    public AgentToolSpec spec() { return new AgentToolSpec(AgentToolName.CREATE_PRACTICE,
            "读取当前课程上下文，供回答阶段生成一道可判分的二选一练习", "{\"type\":\"object\",\"properties\":{}}", true, false, 1000); }
    public AgentToolResult execute(AgentExecutionContext context, JsonNode arguments) { return CurrentContextSupport.execute(context); }
}
