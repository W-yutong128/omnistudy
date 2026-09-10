package com.omnistudy.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgentChatRequest(
        @NotBlank String sessionId,
        @NotBlank String message,
        @NotNull Double currentTime,
        Integer part,
        String before,
        String current,
        String after,
        String screenshot
) {}
