package com.omnistudy.ai;

import lombok.extern.slf4j.Slf4j;
import com.omnistudy.service.AiCredentialService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
@Slf4j
public class AiModelRouter {

    private final Map<String, AiModelAdapter> adapters;
    private final Map<AiTask, ModelRoute> routes = new EnumMap<>(AiTask.class);
    private final AiCredentialService credentialService;

    public AiModelRouter(
            List<AiModelAdapter> adapters,
            AiCredentialService credentialService,
            @Value("${app.ai.models.fast-vision:qwen3-vl-flash}") String fastVision,
            @Value("${app.ai.models.strong-text:qwen-plus}") String strongText) {
        this.credentialService = credentialService;
        this.adapters = adapters.stream().collect(Collectors.toUnmodifiableMap(AiModelAdapter::provider, Function.identity()));
        routes.put(AiTask.CONNECTION_TEST, new ModelRoute("dashscope", strongText, 16, 0.0, false));
        routes.put(AiTask.INTERCEPT, new ModelRoute("dashscope", fastVision, 1200, 0.2, true));
        routes.put(AiTask.EVALUATE_ANSWER, new ModelRoute("dashscope", strongText, 1200, 0.2, false));
        // NOTE_CHUNK_SUMMARY 同时承载增量笔记生成，完整 JSON 可能明显超过 2400 tokens。
        routes.put(AiTask.NOTE_CHUNK_SUMMARY, new ModelRoute("dashscope", strongText, 6000, 0.2, false));
        routes.put(AiTask.NOTE_FINALIZE, new ModelRoute("dashscope", strongText, 10000, 0.2, false));
        routes.put(AiTask.STUDY_MATERIAL, new ModelRoute("dashscope", strongText, 2400, 0.3, false));
        routes.put(AiTask.AGENT_PLAN, new ModelRoute("dashscope", strongText, 600, 0.1, false));
        routes.put(AiTask.AGENT_REPLY, new ModelRoute("dashscope", fastVision, 3000, 0.3, true));
    }

    public String generate(UUID userId, AiTask task, AiRequest request) {
        return generateResult(userId, task, request).content();
    }

    public void requireCredential(UUID userId) {
        credentialService.resolve(userId);
    }

    public AiResult generateResult(UUID userId, AiTask task, AiRequest request) {
        AiCredentialService.ResolvedCredential credential = credentialService.resolve(userId);
        ModelRoute route = routeFor(task, credential);
        if (route == null) throw new IllegalArgumentException("未配置 AI 任务: " + task);
        AiModelAdapter adapter = adapters.get(credential.provider());
        if (adapter == null) throw new IllegalStateException("未安装 AI provider 适配器: " + route.provider());
        log.info("AI route task={} provider={} credentialSource={} model={} vision={}",
                task, credential.provider(), credential.source(), route.model(), request.hasImage());
        return adapter.generate(route, request, credential);
    }

    public ModelRoute routeFor(AiTask task) {
        return routes.get(task);
    }

    private ModelRoute routeFor(AiTask task, AiCredentialService.ResolvedCredential credential) {
        ModelRoute configured = routes.get(task);
        if (configured == null) return null;
        String model = configured.vision() ? credential.fastVisionModel() : credential.strongTextModel();
        return new ModelRoute(credential.provider(), model, configured.maxTokens(), configured.temperature(), configured.vision());
    }
}
