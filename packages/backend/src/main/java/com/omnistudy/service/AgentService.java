package com.omnistudy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnistudy.agent.*;
import com.omnistudy.ai.AiModelRouter;
import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiTask;
import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.model.dto.AgentTraceResponse;
import com.omnistudy.model.dto.InterceptResponse;
import com.omnistudy.model.entity.AgentTrace;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {
    private static final String PROMPT_VERSION = "agent-harness-v2";
    private static final int MAX_STEPS = 1;

    private final AiModelRouter aiModelRouter;
    private final SessionRepository sessionRepository;
    private final AgentTraceService traceService;
    private final AgentToolRegistry toolRegistry;
    private final AgentFallbackRouter fallbackRouter;
    private final AgentSkillRegistry skillRegistry;
    private final ObjectMapper objectMapper;

    @Transactional
    public AgentChatResponse chat(AgentChatRequest request, UUID userId) {
        aiModelRouter.requireCredential(userId);
        Instant started = Instant.now();
        UUID sessionId = UUID.fromString(request.sessionId());
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权访问该 session");

        AgentTrace trace = AgentTrace.builder().userId(userId).sessionId(sessionId).state("RECEIVED")
                .userMessage(request.message()).toolArgs(objectMapper.createObjectNode())
                .promptVersion(PROMPT_VERSION).stepCount(0).latencyMs(0L).success(false).build();
        try {
            AgentSkillRegistry.AgentSkill skill = skillRegistry.select(request.message());
            trace.setSkillName(skill.name()); trace.setSkillVersion(skill.version());
            trace.setState("PLANNING");
            ToolCall call = plan(request, skill, userId);
            trace.setState("TOOL_VALIDATION");
            AgentTool tool = toolRegistry.get(call.name());
            validate(tool.spec(), call.arguments());
            trace.setToolName(call.name().wireName());
            trace.setToolArgs(call.arguments());

            trace.setState(tool.spec().requiresConfirmation() ? "AWAITING_CONFIRMATION" : "TOOL_EXECUTION");
            AgentToolResult result = tool.execute(new AgentExecutionContext(userId, session, request), call.arguments());
            trace.setStepCount(MAX_STEPS);
            trace.setObservation(truncate(result.observation(), 4000));

            trace.setState("FINAL_RESPONSE");
            AgentChatResponse response = reply(call.name(), result, request, session, skill, userId);
            trace.setResponseText(truncate(response.reply(), 4000));
            trace.setSuccess(true);
            trace.setState(result.ideHandoff() == null ? "COMPLETED" : "COMPLETED_AWAITING_CONFIRMATION");
            return response;
        } catch (Exception error) {
            trace.setState("FAILED");
            trace.setErrorMessage(truncate(error.getMessage(), 1000));
            throw error;
        } finally {
            var route = aiModelRouter.routeFor(AiTask.AGENT_REPLY);
            trace.setModelName(route == null ? null : route.model());
            trace.setLatencyMs(Duration.between(started, Instant.now()).toMillis());
            traceService.save(trace);
        }
    }

    private ToolCall plan(AgentChatRequest request, AgentSkillRegistry.AgentSkill skill, UUID userId) {
        StringBuilder tools = new StringBuilder();
        toolRegistry.specs().forEach(spec -> tools.append("- ").append(spec.name().wireName()).append(": ")
                .append(spec.description()).append(" input=").append(spec.inputSchema()).append("\n"));
        String prompt = """
                从白名单中选择且只选择一个工具。只返回 JSON：
                {"tool":"工具名","arguments":{}}
                可用工具：
                %s
                用户消息：%s
                当前字幕：%s
                当前技能约束：%s
                """.formatted(tools, request.message(), value(request.current()), skill.instructions());
        try {
            JsonNode node = objectMapper.readTree(stripFence(aiModelRouter.generate(userId, AiTask.AGENT_PLAN,
                    AiRequest.text("你是学习 Agent 规划器。禁止回答问题或发明工具。", prompt))));
            AgentToolName name = AgentToolName.fromWire(node.path("tool").asText());
            JsonNode arguments = node.path("arguments").isObject() ? node.path("arguments") : objectMapper.createObjectNode();
            if ((name == AgentToolName.SEARCH_LEARNING_MEMORY || name == AgentToolName.SEEK_VIDEO)
                    && arguments.path("query").asText().isBlank()) ((ObjectNode) arguments).put("query", request.message());
            return new ToolCall(name, arguments);
        } catch (Exception ignored) {
            return fallbackPlan(request.message());
        }
    }

    private ToolCall fallbackPlan(String message) {
        String text = value(message);
        AgentToolName name = fallbackRouter.route(text);
        ObjectNode args = objectMapper.createObjectNode();
        if (name == AgentToolName.SEARCH_LEARNING_MEMORY || name == AgentToolName.SEEK_VIDEO) args.put("query", message);
        return new ToolCall(name, args);
    }

    private void validate(AgentToolSpec spec, JsonNode arguments) {
        if (!spec.readOnly() && !spec.requiresConfirmation()) throw new IllegalArgumentException("禁止执行未经确认的有副作用工具");
        if (spec.inputSchema().contains("\"required\"") && arguments.path("query").asText().isBlank()) {
            throw new IllegalArgumentException("工具参数 query 不能为空");
        }
    }

    private AgentChatResponse reply(AgentToolName name, AgentToolResult result,
                                    AgentChatRequest request, StudySession session, AgentSkillRegistry.AgentSkill skill,
                                    UUID userId) {
        boolean practice = name == AgentToolName.CREATE_PRACTICE;
        String prompt = """
                用户正在学习“%s”，用户说：%s
                已执行工具：%s
                工具观察：
                %s
                只返回合法 JSON：{"reply":string,"practice":%s}
                回答必须基于工具观察；引用来源时使用[1][2]。不得声称执行未发生的操作。
                技能约束：%s
                """.formatted(value(session.getVideoTitle()), request.message(), name.wireName(), result.observation(),
                practice ? "{\"question\":string,\"options\":[{\"id\":\"A\",\"text\":string},{\"id\":\"B\",\"text\":string}],\"correctOptionId\":\"A|B\",\"explanation\":string}" : "null",
                skill.instructions());
        try {
            AiRequest aiRequest = request.screenshot() == null || request.screenshot().isBlank()
                    ? AiRequest.text("你是有来源约束的网课学习助理。", prompt)
                    : AiRequest.vision("你是有来源约束的网课学习助理，可结合当前截图。", prompt, request.screenshot());
            JsonNode node = objectMapper.readTree(stripFence(aiModelRouter.generate(userId, AiTask.AGENT_REPLY, aiRequest)));
            return new AgentChatResponse(node.path("reply").asText(result.observation()), name.wireName(), skill.name(),
                    result.sources(), result.seekTo(), parsePractice(node.path("practice")), result.ideHandoff());
        } catch (Exception ignored) {
            return new AgentChatResponse(result.observation(), name.wireName(), skill.name(), result.sources(), result.seekTo(), null, result.ideHandoff());
        }
    }

    private AgentChatResponse.Practice parsePractice(JsonNode node) {
        if (!node.isObject() || !node.path("options").isArray() || node.path("options").size() != 2) return null;
        List<InterceptResponse.Option> options = new ArrayList<>();
        node.path("options").forEach(item -> options.add(new InterceptResponse.Option(item.path("id").asText(), item.path("text").asText())));
        String correct = node.path("correctOptionId").asText();
        if (node.path("question").asText().isBlank() || options.stream().noneMatch(item -> item.id().equals(correct))) return null;
        return new AgentChatResponse.Practice(node.path("question").asText(), options, correct, node.path("explanation").asText());
    }

    private String stripFence(String text) {
        String value = value(text).trim();
        return value.startsWith("```") ? value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "") : value;
    }
    private String value(String value) { return value == null ? "" : value; }
    private String truncate(String value, int max) { if (value == null) return null; return value.length() <= max ? value : value.substring(0, max) + "…"; }
    private record ToolCall(AgentToolName name, JsonNode arguments) {}

    @Transactional(readOnly = true)
    public List<AgentTraceResponse> traces(UUID userId) {
        return traceService.list(userId);
    }
}
