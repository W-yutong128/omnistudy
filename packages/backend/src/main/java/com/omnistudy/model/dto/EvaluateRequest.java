package com.omnistudy.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EvaluateRequest(
    @NotBlank String sessionId,
    @NotNull Integer attempt,
    @NotBlank String questionJson,
    @NotBlank String answer,
    String historyJson
) {}
