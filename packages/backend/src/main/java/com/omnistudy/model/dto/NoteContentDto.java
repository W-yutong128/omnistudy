package com.omnistudy.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NoteContentDto(
    String topic,
    String[] keyPoints,
    String[] formulas,
    String[] codeReferences,
    String summary,
    String[] myWeakPoints
) {}
