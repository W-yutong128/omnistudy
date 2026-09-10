package com.omnistudy.model.dto;

import jakarta.validation.constraints.NotBlank;

public record SessionStartRequest(
    @NotBlank String platform,
    @NotBlank String videoUrl,
    String videoTitle,
    String courseId
) {}
