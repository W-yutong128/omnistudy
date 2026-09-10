package com.omnistudy.controller;

import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.dto.KnowledgePointResponse;
import com.omnistudy.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<KnowledgePointResponse>>> list(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgeService.listResponses(userId)));
    }

    @GetMapping("/weak")
    public ResponseEntity<ApiResponse<List<KnowledgePointResponse>>> weak(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgeService.weakPointResponses(userId)));
    }

    @GetMapping("/due")
    public ResponseEntity<ApiResponse<List<KnowledgePointResponse>>> due(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgeService.duePointResponses(userId)));
    }
}
