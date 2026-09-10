package com.omnistudy.controller;

import com.omnistudy.model.dto.InterceptRequest;
import com.omnistudy.model.dto.InterceptResponse;
import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.dto.ChoiceAnswerRequest;
import com.omnistudy.model.dto.ChoiceAnswerResponse;
import com.omnistudy.service.InterceptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/intercept")
@RequiredArgsConstructor
public class InterceptController {

    private final InterceptService interceptService;

    @PostMapping
    public ResponseEntity<ApiResponse<InterceptService.InterceptResultWithId>> intercept(
            @Valid @RequestBody InterceptRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(interceptService.intercept(request, userId)));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponse<List<InterceptService.StoredQuestion>>> listBySession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(interceptService.listBySession(sessionId, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId
    ) {
        try {
            interceptService.deleteById(id, userId);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("INVALID_ARGUMENT", e.getMessage()));
        }
    }

    @PostMapping("/{id}/answer")
    public ResponseEntity<ApiResponse<ChoiceAnswerResponse>> answerChoice(
            @PathVariable UUID id, @RequestBody ChoiceAnswerRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(interceptService.answerChoice(id, request.optionId(), userId)));
    }
}
