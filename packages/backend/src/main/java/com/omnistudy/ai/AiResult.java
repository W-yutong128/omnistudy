package com.omnistudy.ai;

public record AiResult(
        String content,
        int inputTokens,
        int outputTokens,
        int cachedTokens
) {
}
