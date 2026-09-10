package com.omnistudy.agentscope;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.agent.AgentSkillRegistry;
import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.model.dto.InterceptResponse;
import com.omnistudy.model.entity.AgentTrace;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.repository.SessionRepository;
import com.omnistudy.service.AgentService;
import com.omnistudy.service.AgentTraceService;
import com.omnistudy.service.AgentUsageService;
import com.omnistudy.service.AiCredentialService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentScopeService {
    private static final String PROMPT_VERSION = "agentscope-java-2.0.1-v1";
    private final ObjectProvider<HarnessAgent> agentProvider;
    private final SessionRepository sessionRepository;
    private final AgentSkillRegistry skillRegistry;
    private final AgentScopeContextBuilder contextBuilder;
    private final AgentTraceService traceService;
    private final AgentUsageService usageService;
    private final AgentService legacyAgentService;
    private final ObjectMapper objectMapper;
    private final AiCredentialService credentialService;
    public AgentChatResponse chat(AgentChatRequest request, UUID userId) {
        AiCredentialService.ResolvedCredential credential = credentialService.resolve(userId);
        HarnessAgent agent = agentProvider.getIfAvailable();
        Instant started = Instant.now();
        UUID sessionId = UUID.fromString(request.sessionId());
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) throw new IllegalArgumentException("无权访问该 session");

        var skill = skillRegistry.select(request.message());
        var callContext = new AgentScopeRequestContext(userId, session, request, skill);
        var payload = contextBuilder.build(request, session, skill.instructions());
        UUID usageReservation = usageService.begin(userId, payload.estimatedInputTokens(), "AGENT_V2");
        if (agent == null) {
            boolean success = false;
            try {
                AgentChatResponse response = legacyAgentService.chat(request, userId);
                success = true;
                return response;
            } finally {
                try {
                    usageService.finish(usageReservation, null, success);
                } catch (Exception usageError) {
                    log.error("Agent 降级路径用量记账失败 reservation={}", usageReservation, usageError);
                }
            }
        }
        boolean requestSucceeded = false;
        AgentTrace trace = AgentTrace.builder()
                .userId(userId).sessionId(sessionId).state("AGENTSCOPE_RUNNING")
                .userMessage(request.message()).toolArgs(objectMapper.createObjectNode())
                .modelName(credential.fastVisionModel()).promptVersion(PROMPT_VERSION).skillName(skill.name())
                .skillVersion(skill.version()).framework("agentscope-java-2.0.1")
                .inputTokens(payload.estimatedInputTokens()).outputTokens(0).cachedTokens(0)
                .stepCount(0).latencyMs(0L).success(false).build();
        try {
            RuntimeContext runtimeContext = RuntimeContext.builder()
                    .userId(userId.toString())
                    .sessionId(sessionId.toString())
                    .put(AgentScopeRequestContext.class, callContext)
                    .put("screenshot_available", payload.screenshotAvailable())
                    .build();
            Msg message = agent.call(new UserMessage(payload.prompt()), runtimeContext)
                    .contextWrite(context -> context.put(MeteredAgentScopeModel.USER_ID_CONTEXT_KEY, userId))
                    .block(Duration.ofSeconds(50));
            if (message == null) throw new IllegalStateException("AgentScope 未返回终止消息");

            ChatUsage usage = message.getChatUsage();
            if (usage != null) {
                trace.setInputTokens(usage.getInputTokens());
                trace.setOutputTokens(usage.getOutputTokens());
                trace.setCachedTokens(usage.getCachedTokens());
            }
            AgentChatResponse response = response(message.getTextContent(), callContext);
            trace.setToolName(callContext.tool());
            trace.setObservation(truncate(callContext.observation(), 4000));
            trace.setResponseText(truncate(response.reply(), 4000));
            trace.setStepCount(callContext.steps());
            trace.setState(response.ideHandoff() == null ? "COMPLETED" : "COMPLETED_AWAITING_CONFIRMATION");
            trace.setSuccess(true);
            requestSucceeded = true;
            return response;
        } catch (Exception error) {
            trace.setState("AGENTSCOPE_FAILED_FALLBACK");
            trace.setErrorMessage(truncate(rootMessage(error), 1000));
            AgentChatResponse fallback = legacyAgentService.chat(request, userId);
            requestSucceeded = true;
            return fallback;
        } finally {
            trace.setLatencyMs(Duration.between(started, Instant.now()).toMillis());
            AgentTrace saved = traceService.save(trace);
            try {
                usageService.finish(usageReservation, saved, requestSucceeded);
            } catch (Exception usageError) {
                log.error("Agent 用量记账失败 reservation={}", usageReservation, usageError);
            }
        }
    }

    private AgentChatResponse response(String raw, AgentScopeRequestContext context) {
        String reply = raw == null ? "" : raw.trim();
        AgentChatResponse.Practice practice = null;
        try {
            JsonNode json = objectMapper.readTree(stripFence(reply));
            if (json.isObject()) {
                reply = json.path("reply").asText(reply);
                practice = parsePractice(json.path("practice"));
            }
        } catch (Exception ignored) {
            // AgentScope can still return useful plain text if a provider ignores JSON instructions.
        }
        if (reply.isBlank()) reply = context.observation().isBlank() ? "暂时无法生成回答。" : context.observation();
        return new AgentChatResponse(reply, context.tool(), context.skill().name(), context.sources(),
                context.seekTo(), practice, context.ideHandoff());
    }

    private AgentChatResponse.Practice parsePractice(JsonNode node) {
        if (!node.isObject() || !node.path("options").isArray() || node.path("options").size() < 2) return null;
        List<InterceptResponse.Option> options = new ArrayList<>();
        node.path("options").forEach(item -> options.add(new InterceptResponse.Option(
                item.path("id").asText(), item.path("text").asText())));
        String correct = node.path("correctOptionId").asText();
        if (node.path("question").asText().isBlank() || options.stream().noneMatch(item -> item.id().equals(correct))) return null;
        return new AgentChatResponse.Practice(node.path("question").asText(), options, correct,
                node.path("explanation").asText());
    }

    private String stripFence(String text) {
        return text.startsWith("```") ? text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "") : text;
    }
    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
