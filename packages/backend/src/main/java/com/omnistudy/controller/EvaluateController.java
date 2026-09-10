package com.omnistudy.controller;

import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.dto.EvaluateRequest;
import com.omnistudy.model.dto.EvaluateResponse;
import com.omnistudy.service.EvaluateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/evaluate")
@RequiredArgsConstructor
public class EvaluateController {

    private final EvaluateService evaluateService;

    @PostMapping
    public ResponseEntity<ApiResponse<EvaluateResponse>> evaluate(
            @Valid @RequestBody EvaluateRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(evaluateService.evaluate(request, userId)));
    }
}