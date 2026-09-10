package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.ai.AiModelRouter;
import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiTask;
import com.omnistudy.agent.AgentToolRegistry;
import com.omnistudy.agent.ExplainCurrentTool;
import com.omnistudy.agent.RespondTool;
import com.omnistudy.agent.RequestOpenIdeaTool;
import com.omnistudy.agent.AgentFallbackRouter;
import com.omnistudy.agent.AgentSkillRegistry;
import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.repository.SessionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTest {
    private final AiModelRouter router = mock(AiModelRouter.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final AgentTraceService traces = mock(AgentTraceService.class);
    private final AgentService service = new AgentService(router, sessions, traces,
            new AgentToolRegistry(java.util.List.of(new ExplainCurrentTool(), new RespondTool(), new RequestOpenIdeaTool())),
            new AgentFallbackRouter(), new AgentSkillRegistry(), new ObjectMapper());

    @Test
    void explainsCurrentContextThroughBoundedTool() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(StudySession.builder()
                .id(sessionId).userId(userId).videoTitle("操作系统").build()));
        when(router.generate(eq(userId), eq(AiTask.AGENT_PLAN), any(AiRequest.class)))
                .thenReturn("{\"tool\":\"explain_current\",\"query\":\"\"}");
        when(router.generate(eq(userId), eq(AiTask.AGENT_REPLY), any(AiRequest.class)))
                .thenReturn("{\"reply\":\"这是进程调度的基本概念。[1]\",\"practice\":null}");
        when(router.routeFor(AiTask.AGENT_REPLY)).thenReturn(new com.omnistudy.ai.ModelRoute("dashscope", "test", 100, 0.1, true));

        var response = service.chat(new AgentChatRequest(sessionId.toString(), "解释当前内容",
                62.0, 1, "前文", "进程调度", "", null), userId);

        assertEquals("explain_current", response.tool());
        assertEquals("这是进程调度的基本概念。[1]", response.reply());
        assertEquals(1, response.sources().size());
    }

    @Test
    void rejectsAnotherUsersSession() {
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(StudySession.builder()
                .id(sessionId).userId(UUID.randomUUID()).build()));

        assertThrows(IllegalArgumentException.class, () -> service.chat(new AgentChatRequest(
                sessionId.toString(), "解释", 0.0, 1, "", "", "", null), UUID.randomUUID()));
    }

    @Test
    void ideaHandoffRequiresUserConfirmation() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(StudySession.builder()
                .id(sessionId).userId(userId).videoTitle("Java 实战").build()));
        when(router.generate(eq(userId), eq(AiTask.AGENT_PLAN), any(AiRequest.class)))
                .thenReturn("{\"tool\":\"request_open_idea\",\"arguments\":{\"reason\":\"动手实现\",\"suggestedFile\":\"src/Main.java\"}}");
        when(router.generate(eq(userId), eq(AiTask.AGENT_REPLY), any(AiRequest.class)))
                .thenReturn("{\"reply\":\"可以进入 IDEA 动手实现。\",\"practice\":null}");

        var response = service.chat(new AgentChatRequest(sessionId.toString(), "打开 IDEA 写代码",
                10.0, 1, "", "实现二叉树", "", null), userId);

        assertEquals("request_open_idea", response.tool());
        assertEquals("src/Main.java", response.ideHandoff().suggestedFile());
    }
}
