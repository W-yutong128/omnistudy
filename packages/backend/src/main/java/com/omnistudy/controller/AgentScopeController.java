package com.omnistudy.controller;

import com.omnistudy.agentscope.AgentScopeService;
import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.model.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/agent-v2")
@RequiredArgsConstructor
public class AgentScopeController {
    private final AgentScopeService service;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<AgentChatResponse>> chat(
            @Valid @RequestBody AgentChatRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.chat(request, userId)));
    }
}
