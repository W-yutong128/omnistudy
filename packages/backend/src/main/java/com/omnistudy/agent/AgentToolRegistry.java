package com.omnistudy.agent;
import org.springframework.stereotype.Component;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
@Component
public class AgentToolRegistry {
    private final Map<AgentToolName, AgentTool> tools = new EnumMap<>(AgentToolName.class);
    public AgentToolRegistry(List<AgentTool> registered) { registered.forEach(tool -> tools.put(tool.spec().name(), tool)); }
    public AgentTool get(AgentToolName name) {
        AgentTool tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("未注册 Agent 工具: " + name.wireName());
        return tool;
    }
    public List<AgentToolSpec> specs() { return tools.values().stream().map(AgentTool::spec).toList(); }
}
