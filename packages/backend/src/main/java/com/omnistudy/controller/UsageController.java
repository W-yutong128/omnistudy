package com.omnistudy.controller;

import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.dto.UserUsageOverviewResponse;
import com.omnistudy.service.AgentUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/usage")
@RequiredArgsConstructor
public class UsageController {
    private final AgentUsageService usageService;

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<UserUsageOverviewResponse>> today(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(usageService.todayOverview(userId)));
    }
}
