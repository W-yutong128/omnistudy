package com.omnistudy.repository.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.model.dto.ApiResponse;
import com.omnistudy.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DistributedRateLimitFilter extends OncePerRequestFilter {
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;
    @Value("${app.rate-limit.auth-login-limit:10}")
    private int loginLimit;
    @Value("${app.rate-limit.auth-register-limit:5}")
    private int registerLimit;
    @Value("${app.rate-limit.auth-refresh-limit:30}")
    private int refreshLimit;
    @Value("${app.rate-limit.ai-user-limit:30}")
    private int aiUserLimit;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Policy policy = enabled ? policy(request) : null;
        if (policy == null) {
            chain.doFilter(request, response);
            return;
        }

        RateLimitService.Decision decision = rateLimitService.check(policy.bucket, policy.subject, policy.limit,
                policy.window);
        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail("RATE_LIMITED", "请求过于频繁，请稍后重试"));
    }

    private Policy policy(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return null;
        String path = request.getRequestURI();
        String remote = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if ("/api/auth/login".equals(path)) return new Policy("auth-login", remote, loginLimit, Duration.ofMinutes(1));
        if ("/api/auth/register".equals(path)) return new Policy("auth-register", remote, registerLimit, Duration.ofHours(1));
        if ("/api/auth/refresh".equals(path)) return new Policy("auth-refresh", remote, refreshLimit, Duration.ofMinutes(1));
        if (!isAiRequest(path)) return null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID userId)) return null;
        return new Policy("ai-user", userId.toString(), aiUserLimit, Duration.ofMinutes(1));
    }

    private boolean isAiRequest(String path) {
        return "/api/agent/chat".equals(path)
                || "/api/agent-v2/chat".equals(path)
                || "/api/intercept".equals(path)
                || "/api/note/generate".equals(path)
                || path.matches("/api/note/session/[^/]+/auto-sync")
                || path.matches("/api/note/[^/]+/study-materials");
    }

    private record Policy(String bucket, String subject, int limit, Duration window) {}
}
