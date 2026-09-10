package com.omnistudy.model.dto;

public record AuthResponse(
    String token,
    String refreshToken,
    String userId,
    String username,
    String role,
    String expiresAt
) {}
