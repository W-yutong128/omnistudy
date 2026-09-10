package com.omnistudy.model.dto;

import com.omnistudy.model.entity.UserRole;
import com.omnistudy.model.entity.UserStatus;
import jakarta.validation.constraints.Min;

public record AdminUpdateUserRequest(
        UserRole role,
        UserStatus status,
        @Min(0) Integer dailyRequestLimit,
        @Min(0) Long dailyTokenLimit,
        Boolean unlimited
) {}
