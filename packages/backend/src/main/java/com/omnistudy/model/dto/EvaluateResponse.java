package com.omnistudy.model.dto;

public record EvaluateResponse(
    String decision,     // pass / follow_up / support
    String feedback,
    String nextQuestion,
    String hint,
    int attempt
) {}
