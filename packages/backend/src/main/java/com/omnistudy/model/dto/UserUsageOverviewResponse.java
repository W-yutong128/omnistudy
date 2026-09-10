package com.omnistudy.model.dto;

import java.util.List;

public record UserUsageOverviewResponse(
        String periodStart,
        long requests,
        long inputTokens,
        long outputTokens,
        long cachedTokens,
        long totalTokens,
        long estimatedCostMicros,
        long succeeded,
        long failed,
        List<FeatureUsage> features
) {
    public record FeatureUsage(
            String feature,
            long requests,
            long inputTokens,
            long outputTokens,
            long cachedTokens,
            long totalTokens,
            long estimatedCostMicros,
            long succeeded,
            long failed
    ) {}
}
