package com.omnistudy.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AiProviderSettingsRequest(
        @NotBlank(message = "API Key 不能为空")
        @Size(max = 500, message = "API Key 过长")
        String apiKey,
        @Pattern(regexp = "^[A-Za-z0-9._:/-]{1,100}$", message = "视觉模型名称格式不正确")
        String fastVisionModel,
        @Pattern(regexp = "^[A-Za-z0-9._:/-]{1,100}$", message = "文本模型名称格式不正确")
        String strongTextModel
) {}

