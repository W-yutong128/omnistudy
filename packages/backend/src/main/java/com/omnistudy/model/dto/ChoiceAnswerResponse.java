package com.omnistudy.model.dto;

public record ChoiceAnswerResponse(
        boolean correct,
        String correctOptionId,
        String explanation,
        Double masteryScore,
        String mastery,
        String nextReviewAt
) {}
