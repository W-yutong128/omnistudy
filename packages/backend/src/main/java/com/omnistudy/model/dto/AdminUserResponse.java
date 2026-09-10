package com.omnistudy.model.dto;

public record AdminUserResponse(
        String id, String username, String email, String role, String status,
        boolean emailVerified, long dailyRequestLimit, long dailyTokenLimit, boolean unlimited,
        long requestsUsedToday, long tokensUsedToday, String createdAt, String lastLoginAt
) {}
