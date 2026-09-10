package com.omnistudy.exception;

import com.omnistudy.model.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("AUTH_BAD_CREDENTIALS", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.fail("RESOURCE_CONFLICT", ex.getMessage()));
    }

    @ExceptionHandler(AccountDisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(AccountDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("ACCOUNT_DISABLED", ex.getMessage()));
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleQuota(QuotaExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.fail("QUOTA_EXCEEDED", ex.getMessage()));
    }

    @ExceptionHandler(MissingAiCredentialException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingAiCredential(MissingAiCredentialException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.fail("AI_CREDENTIAL_MISSING", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("FORBIDDEN", "没有权限执行该操作"));
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingAuthentication(AuthenticationCredentialsNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("AUTH_REQUIRED", "请先登录"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail("INVALID_ARGUMENT", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_FAILED", msg));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiResponse<Void>> handleWebClient(WebClientResponseException ex) {
        int upstreamStatus = ex.getStatusCode().value();
        HttpStatus responseStatus;
        String code;
        String message;
        if (upstreamStatus == 401 || upstreamStatus == 403) {
            responseStatus = HttpStatus.UNPROCESSABLE_ENTITY;
            code = "AI_CREDENTIAL_REJECTED";
            message = "模型服务拒绝了 API Key，请检查个人模型配置";
        } else if (upstreamStatus == 429) {
            responseStatus = HttpStatus.TOO_MANY_REQUESTS;
            code = "AI_RATE_LIMITED";
            message = "模型服务请求过于频繁，请稍后重试";
        } else if (upstreamStatus >= 400 && upstreamStatus < 500) {
            responseStatus = HttpStatus.UNPROCESSABLE_ENTITY;
            code = "AI_REQUEST_REJECTED";
            message = "模型服务无法处理本次请求，请检查模型配置";
        } else {
            responseStatus = HttpStatus.BAD_GATEWAY;
            code = "AI_PROVIDER_UNAVAILABLE";
            message = "模型服务暂时不可用，请稍后重试";
        }
        String traceId = newTraceId();
        log.warn("AI upstream request failed traceId={} method={} uri={} status={}", traceId,
                ex.getRequest() != null ? ex.getRequest().getMethod() : "?",
                ex.getRequest() != null ? ex.getRequest().getURI() : "?",
                upstreamStatus);
        return failure(responseStatus, code, message, traceId);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        String traceId = newTraceId();
        log.error("Runtime error traceId={}", traceId, ex);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误，请稍后重试", traceId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        String traceId = newTraceId();
        log.error("Unexpected error traceId={}", traceId, ex);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误，请稍后重试", traceId);
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String code, String message, String traceId) {
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message + "（参考号：" + traceId + "）", traceId));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
