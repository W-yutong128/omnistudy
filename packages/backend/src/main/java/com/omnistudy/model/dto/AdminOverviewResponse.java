package com.omnistudy.model.dto;

import java.util.List;

public record AdminOverviewResponse(long users, long activeUsers, long requestsToday,
                                    long tokensToday, long estimatedCostMicrosToday,
                                    List<FeatureUsage> features) {
    public record FeatureUsage(String feature, long requests, long tokens, long estimatedCostMicros) {}
}
