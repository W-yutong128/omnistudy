package com.omnistudy.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
    @NotBlank @Size(min = 3, max = 64) String username,
    @NotBlank @Size(min = 6, max = 100) String password
) {}
