package com.omnistudy.controller;

import com.omnistudy.model.dto.*;
import com.omnistudy.service.QuestionBankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/question-bank")
@RequiredArgsConstructor
public class QuestionBankController {
    private final QuestionBankService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<QuestionBankResponse>>> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String subject,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.search(query, subject, userId, limit)));
    }

    @PostMapping("/{id}/deliver")
    public ResponseEntity<ApiResponse<DeliveredQuestionResponse>> deliver(
            @PathVariable UUID id,
            @RequestBody(required = false) QuestionDeliveryRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.deliver(id, request, userId)));
    }
}
