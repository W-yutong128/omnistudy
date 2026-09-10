package com.omnistudy.agentscope;

import com.omnistudy.service.AgentUsageService;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.memory.compaction.TokenCounterUtil;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Adds quota and cost accounting to AgentScope's internal model calls. */
@Slf4j
final class MeteredAgentScopeModel implements Model {
    static final String USER_ID_CONTEXT_KEY = "omnistudy.agent.user-id";

    private final Model delegate;
    private final AgentUsageService usageService;
    private final String feature;

    MeteredAgentScopeModel(Model delegate, AgentUsageService usageService, String feature) {
        this.delegate = delegate;
        this.usageService = usageService;
        this.feature = feature;
    }

    @Override
    public Flux<io.agentscope.core.model.ChatResponse> stream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.deferContextual(context -> {
            UUID userId = context.getOrDefault(USER_ID_CONTEXT_KEY, null);
            if (userId == null) return delegate.stream(messages, tools, options);

            UUID reservation = usageService.beginInternal(
                    userId, Math.max(1, TokenCounterUtil.calculateToken(messages)), feature);
            AtomicReference<ChatUsage> actual = new AtomicReference<>();
            AtomicBoolean finished = new AtomicBoolean(false);

            return delegate.stream(messages, tools, options)
                    .doOnNext(response -> {
                        if (response.getUsage() != null) actual.set(response.getUsage());
                    })
                    .doOnComplete(() -> finish(reservation, actual.get(), true, finished))
                    .doOnError(error -> finish(reservation, actual.get(), false, finished))
                    .doOnCancel(() -> finish(reservation, actual.get(), false, finished));
        });
    }

    private void finish(UUID reservation, ChatUsage usage, boolean success, AtomicBoolean finished) {
        if (!finished.compareAndSet(false, true)) return;
        try {
            usageService.finish(reservation,
                    usage == null ? 0 : usage.getInputTokens(),
                    usage == null ? 0 : usage.getOutputTokens(),
                    usage == null ? 0 : usage.getCachedTokens(), success);
        } catch (Exception error) {
            log.error("AgentScope internal usage finalization failed reservation={} feature={}",
                    reservation, feature, error);
        }
    }

    @Override public String getModelName() { return delegate.getModelName(); }
    @Override public boolean supportsNativeStructuredOutput() { return delegate.supportsNativeStructuredOutput(); }
    @Override public boolean supportsNativeStructuredOutputWithTools() { return delegate.supportsNativeStructuredOutputWithTools(); }
    @Override public int getContextWindowSize() { return delegate.getContextWindowSize(); }
}
