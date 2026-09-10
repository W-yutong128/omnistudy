package com.omnistudy.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 64)
        @Pattern(regexp = "^[\\p{L}\\p{N}_-]+$", message = "用户名只能包含文字、字母、数字、下划线和短横线")
        String username,
        @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 100) String password
) {}
