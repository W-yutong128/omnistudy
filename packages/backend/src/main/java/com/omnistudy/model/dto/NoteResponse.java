package com.omnistudy.model.dto;

public record NoteResponse(
    String id,
    String sessionId,
    NoteContentDto content,
    String generatedAt,
    String status,
    String error
) {}
