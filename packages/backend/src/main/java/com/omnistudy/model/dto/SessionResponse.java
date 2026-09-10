package com.omnistudy.model.dto;

public record SessionResponse(
    String id,
    String userId,
    String courseId,
    String platform,
    String videoUrl,
    String videoTitle,
    String startedAt,
    String endedAt,
    int questionCount
) {}
