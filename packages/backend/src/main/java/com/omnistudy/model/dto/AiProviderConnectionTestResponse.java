package com.omnistudy.model.dto;

public record AiProviderConnectionTestResponse(
        boolean reachable,
        String provider,
        String model,
        long latencyMs,
        String message
) {}
