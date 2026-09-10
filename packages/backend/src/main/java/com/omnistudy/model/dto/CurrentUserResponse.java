package com.omnistudy.model.dto;

public record CurrentUserResponse(
        String id,
        String username,
        String email,
        String role,
        String status,
        boolean emailVerified,
        Quota quota
) {
    public record Quota(long dailyRequestLimit, long dailyTokenLimit, boolean unlimited,
                        long requestsUsedToday, long tokensUsedToday) {}
}
