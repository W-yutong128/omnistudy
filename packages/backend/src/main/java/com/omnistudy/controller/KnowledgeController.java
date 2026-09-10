package com.omnistudy.controller;

import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.entity.KnowledgePoint;
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
    public ResponseEntity<ApiResponse<List<KnowledgePoint>>> list(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgeService.listByUser(userId)));
    }

    @GetMapping("/weak")
    public ResponseEntity<ApiResponse<List<KnowledgePoint>>> weak(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgeService.weakPoints(userId)));
    }

    @GetMapping("/due")
    public ResponseEntity<ApiResponse<List<KnowledgePoint>>> due(
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(knowledgeService.duePoints(userId)));
    }
}
