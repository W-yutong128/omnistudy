package com.omnistudy.controller;

import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.dto.SessionResponse;
import com.omnistudy.service.SessionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<SessionResponse>> start(
            @RequestBody StartRequest req,
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.start(userId, req.getVideoUrl(), req.getVideoTitle())));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<ApiResponse<SessionResponse>> end(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.end(id, userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> list(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(sessionService.listByUser(userId)));
    }

    @Data
    public static class StartRequest {
        private String videoUrl;
        private String videoTitle;
    }
}
