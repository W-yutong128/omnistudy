package com.omnistudy.agentscope;

import com.omnistudy.agent.AgentSkillRegistry;
import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.service.KnowledgeService;
import com.omnistudy.service.RagService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentScopeContextBuilderTest {
    @Test
    void boundsLargeBrowserContextAndEstimatesTokens() {
        String large = "字幕".repeat(20_000);
        var request = new AgentChatRequest(UUID.randomUUID().toString(), "解释", 10D, 1,
                large, large, large, "data:image/jpeg;base64,abc");
        var payload = new AgentScopeContextBuilder().build(request,
                StudySession.builder().videoTitle("测试课程").build(), "先提示再解释");

        assertTrue(payload.prompt().length() <= 24_001);
        assertTrue(payload.estimatedInputTokens() > 0);
        assertTrue(payload.screenshotAvailable());
        assertTrue(payload.prompt().contains("当前字幕"));
    }

    @Test
    void ideaToolOnlyProducesAConfirmationHandoff() {
        var context = new AgentScopeRequestContext(UUID.randomUUID(),
                StudySession.builder().videoTitle("Java").build(),
                new AgentChatRequest(UUID.randomUUID().toString(), "打开 IDEA", 0D, 1, "", "代码", "", null),
                new AgentSkillRegistry().select("Java 代码"));
        var tools = new OmniStudyAgentScopeTools(mock(RagService.class), mock(KnowledgeService.class));

        String observation = tools.requestOpenIdea("动手练习", "src/Main.java", context);

        assertNotNull(context.ideHandoff());
        assertEquals("src/Main.java", context.ideHandoff().suggestedFile());
        assertTrue(observation.contains("确认"));
    }
}
