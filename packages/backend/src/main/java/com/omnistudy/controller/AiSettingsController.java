package com.omnistudy.controller;

import com.omnistudy.model.dto.AiProviderSettingsRequest;
import com.omnistudy.model.dto.AiProviderSettingsResponse;
import com.omnistudy.model.dto.AiProviderConnectionTestResponse;
import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.service.AiCredentialService;
import com.omnistudy.service.AiProviderConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/settings/ai-provider")
@RequiredArgsConstructor
public class AiSettingsController {
    private final AiCredentialService service;
    private final AiProviderConnectionService connectionService;

    @GetMapping
    public ResponseEntity<ApiResponse<AiProviderSettingsResponse>> settings(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.settings(userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<AiProviderSettingsResponse>> save(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AiProviderSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.save(userId, request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<AiProviderSettingsResponse>> delete(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.delete(userId)));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<AiProviderConnectionTestResponse>> test(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(connectionService.test(userId)));
    }
}
