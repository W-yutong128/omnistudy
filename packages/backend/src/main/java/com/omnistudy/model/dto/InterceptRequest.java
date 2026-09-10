package com.omnistudy.model.dto;

import jakarta.validation.constraints.*;

public record InterceptRequest(
    @NotBlank(message = "sessionId 不能为空")
    String sessionId,

    @NotBlank(message = "time 不能为空")
    String time,

    String screenshot, // base64

    String before,
    String current,
    String after,

    @NotNull @Min(1) @Max(3)
    Integer difficulty,

    Integer part
) {}
