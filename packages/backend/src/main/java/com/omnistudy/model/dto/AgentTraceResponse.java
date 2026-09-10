package com.omnistudy.model.dto;

public record AgentTraceResponse(
        String id, String state, String tool, String skill, String model,
        String promptVersion, String framework, int inputTokens, int outputTokens, int cachedTokens,
        int steps, long latencyMs, boolean success,
        String error, String createdAt
) {}
