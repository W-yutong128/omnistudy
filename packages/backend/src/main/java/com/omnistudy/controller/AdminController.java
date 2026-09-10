package com.omnistudy.controller;

import com.omnistudy.model.dto.*;
import com.omnistudy.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService service;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<AdminOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok(service.overview()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> users() {
        return ResponseEntity.ok(ApiResponse.ok(service.users()));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> update(
            @AuthenticationPrincipal UUID actorId, @PathVariable UUID userId,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(actorId, userId, request)));
    }
}
