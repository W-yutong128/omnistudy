package com.omnistudy.repository.security;

import com.omnistudy.model.entity.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CachedUserAccessService cachedUserAccessService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtil.validate(token)) {
                try {
                    UUID userId = jwtUtil.parseUserId(token);
                    cachedUserAccessService.find(userId)
                            .filter(user -> user.status() == UserStatus.ACTIVE)
                            .ifPresent(user -> {
                                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
                                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            });
                } catch (Exception e) {
                    log.warn("JWT 解析失败: {}", e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
