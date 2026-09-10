package com.omnistudy.service;

import com.omnistudy.ai.AiModelRouter;
import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiResult;
import com.omnistudy.ai.AiTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MeteredAiService {
    private final AiModelRouter router;
    private final AgentUsageService usageService;

    public String generate(UUID userId, AiTask task, AiRequest request) {
        router.requireCredential(userId);
        UUID reservation = usageService.begin(userId, estimateInputTokens(request), task.name());
        return generateReserved(userId, reservation, task, request);
    }

    public String generateForOperation(UUID userId, AiTask task, AiRequest request, String operationKey) {
        router.requireCredential(userId);
        UUID reservation = usageService.beginOperation(
                userId, estimateInputTokens(request), task.name(), operationKey);
        return generateReserved(userId, reservation, task, request);
    }

    private String generateReserved(UUID userId, UUID reservation, AiTask task, AiRequest request) {
        try {
            AiResult result = router.generateResult(userId, task, request);
            finishQuietly(reservation, result.inputTokens(), result.outputTokens(), result.cachedTokens(), true);
            return result.content();
        } catch (RuntimeException error) {
            finishQuietly(reservation, 0, 0, 0, false);
            throw error;
        }
    }

    private int estimateInputTokens(AiRequest request) {
        int chars = length(request.systemPrompt()) + length(request.userPrompt());
        int imageReserve = request.hasImage() ? 1_000 : 0;
        return Math.max(1, chars / 2 + imageReserve);
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private void finishQuietly(UUID reservation, int inputTokens, int outputTokens, int cachedTokens,
                               boolean success) {
        try {
            usageService.finish(reservation, inputTokens, outputTokens, cachedTokens, success);
        } catch (RuntimeException error) {
            log.error("AI 用量回填失败 reservation={}", reservation, error);
        }
    }
}
