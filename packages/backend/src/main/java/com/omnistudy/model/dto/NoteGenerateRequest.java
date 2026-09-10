package com.omnistudy.model.dto;

import jakarta.validation.constraints.NotBlank;

public record NoteGenerateRequest(@NotBlank String sessionId) {}
