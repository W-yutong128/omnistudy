package com.omnistudy.model.dto;

import java.util.List;

public record KnowledgePointResponse(
        String id,
        String name,
        String normalizedName,
        String mastery,
        String firstSeen,
        String lastReviewed,
        double masteryScore,
        String nextReviewAt,
        int reviewIntervalDays,
        int correctStreak,
        List<String> sources,
        int noteCount
) {}
