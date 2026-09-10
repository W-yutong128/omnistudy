package com.omnistudy.controller;

import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.model.dto.AuthRequest;
import com.omnistudy.model.dto.AuthResponse;
import com.omnistudy.model.dto.CurrentUserResponse;
import com.omnistudy.model.dto.RefreshTokenRequest;
import com.omnistudy.model.dto.RegisterRequest;
import com.omnistudy.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            @org.springframework.security.core.annotation.AuthenticationPrincipal java.util.UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(userId)));
    }
}
