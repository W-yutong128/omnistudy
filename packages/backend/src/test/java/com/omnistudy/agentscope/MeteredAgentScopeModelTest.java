package com.omnistudy.agentscope;

import com.omnistudy.service.AgentUsageService;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MeteredAgentScopeModelTest {
    @Test
    void recordsProviderUsageAsInternalTokens() {
        AgentUsageService usageService = mock(AgentUsageService.class);
        UUID userId = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        when(usageService.beginInternal(eq(userId), anyInt(), eq("CONTEXT_COMPACTION")))
                .thenReturn(reservation);
        Model delegate = new StubModel(Flux.just(ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("summary").build()))
                .usage(new ChatUsage(640, 120, 80, 0.2))
                .finishReason("stop")
                .build()));
        MeteredAgentScopeModel model = new MeteredAgentScopeModel(
                delegate, usageService, "CONTEXT_COMPACTION");

        model.stream(List.of(message("history")), null, null)
                .contextWrite(context -> context.put(MeteredAgentScopeModel.USER_ID_CONTEXT_KEY, userId))
                .blockLast();

        verify(usageService).beginInternal(eq(userId), intThat(value -> value > 0),
                eq("CONTEXT_COMPACTION"));
        verify(usageService).finish(reservation, 640, 120, 80, true);
    }

    @Test
    void bypassesBillingWhenNoAuthenticatedContextExists() {
        AgentUsageService usageService = mock(AgentUsageService.class);
        MeteredAgentScopeModel model = new MeteredAgentScopeModel(
                new StubModel(Flux.just(ChatResponse.builder()
                        .content(List.of(TextBlock.builder().text("ok").build())).build())),
                usageService, "AGENT_MEMORY");

        model.stream(List.of(message("history")), null, null).blockLast();

        verifyNoInteractions(usageService);
    }

    private static Msg message(String text) {
        return Msg.builder().role(MsgRole.USER)
                .content(TextBlock.builder().text(text).build()).build();
    }

    private record StubModel(Flux<ChatResponse> responses) implements Model {
        @Override public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools,
                                                    GenerateOptions options) { return responses; }
        @Override public String getModelName() { return "stub"; }
    }
}
