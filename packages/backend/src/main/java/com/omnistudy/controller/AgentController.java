package com.omnistudy.controller;

import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.dto.AgentTraceResponse;
import com.omnistudy.service.AgentService;
import com.omnistudy.service.AgentUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService agentService;
    private final AgentUsageService usageService;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AgentChatResponse>> chat(
            @Valid @RequestBody AgentChatRequest request,
            @AuthenticationPrincipal UUID userId) {
        int estimate = Math.max(1, (request.message().length() +
                (request.before() == null ? 0 : request.before().length()) +
                (request.current() == null ? 0 : request.current().length()) +
                (request.after() == null ? 0 : request.after().length())) / 2);
        UUID reservation = usageService.begin(userId, estimate);
        boolean success = false;
        try {
            AgentChatResponse response = agentService.chat(request, userId);
            success = true;
            return ResponseEntity.ok(ApiResponse.ok(response));
        } finally {
            try {
                usageService.finish(reservation, null, success);
            } catch (Exception ignored) {
                // A metering failure must not hide the original Agent response.
            }
        }
    }

    @GetMapping("/traces")
    public ResponseEntity<ApiResponse<List<AgentTraceResponse>>> traces(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.traces(userId)));
    }
}
