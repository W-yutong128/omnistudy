package com.omnistudy.model.dto;

public record AiProviderSettingsResponse(
        boolean configured,
        String source,
        String provider,
        String baseUrl,
        String maskedApiKey,
        String fastVisionModel,
        String strongTextModel,
        String connectionStatus,
        String lastVerifiedAt,
        String lastTestError
) {}
