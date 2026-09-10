package com.omnistudy.agentscope;

import com.omnistudy.exception.MissingAiCredentialException;
import com.omnistudy.service.AiCredentialService;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/** Resolves an encrypted user-owned API key at subscription time, never at application startup. */
final class UserAwareDashScopeModel implements Model {
    private final AiCredentialService credentialService;
    private final boolean textModel;

    UserAwareDashScopeModel(AiCredentialService credentialService, boolean textModel) {
        this.credentialService = credentialService;
        this.textModel = textModel;
    }

    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.deferContextual(context -> {
            UUID userId = context.getOrDefault(MeteredAgentScopeModel.USER_ID_CONTEXT_KEY, null);
            if (userId == null) {
                return Flux.error(new MissingAiCredentialException("Agent 调用缺少用户身份"));
            }
            AiCredentialService.ResolvedCredential credential = credentialService.resolve(userId);
            String modelName = textModel ? credential.strongTextModel() : credential.fastVisionModel();
            Model delegate = DashScopeChatModel.builder()
                    .apiKey(credential.apiKey())
                    .modelName(modelName)
                    .stream(false)
                    .enableThinking(false)
                    .defaultOptions(GenerateOptions.builder()
                            .temperature(0.2).maxTokens(3000).parallelToolCalls(false).build())
                    .build();
            return delegate.stream(messages, tools, options);
        });
    }

    @Override public String getModelName() { return textModel ? "user-strong-text" : "user-fast-vision"; }
    @Override public boolean supportsNativeStructuredOutput() { return false; }
    @Override public boolean supportsNativeStructuredOutputWithTools() { return false; }
    @Override public int getContextWindowSize() { return 128_000; }
}

