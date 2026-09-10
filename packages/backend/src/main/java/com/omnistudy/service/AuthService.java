package com.omnistudy.service;

import com.omnistudy.exception.AccountDisabledException;
import com.omnistudy.exception.BadCredentialsException;
import com.omnistudy.exception.ConflictException;
import com.omnistudy.model.dto.*;
import com.omnistudy.model.entity.*;
import com.omnistudy.repository.RefreshTokenRepository;
import com.omnistudy.repository.UsageRecordRepository;
import com.omnistudy.repository.UserQuotaRepository;
import com.omnistudy.repository.UserRepository;
import com.omnistudy.repository.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final UserQuotaRepository quotaRepository;
    private final UsageRecordRepository usageRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.refresh-expiration-days:30}")
    private long refreshExpirationDays;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByUsername(username)) throw new ConflictException("用户名已被使用");
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) throw new ConflictException("邮箱已被使用");

        User user = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build());
        quotaRepository.save(UserQuota.builder().userId(user.getId()).build());
        return issue(user);
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByUsername(normalizeUsername(request.username()))
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        ensureActive(user);
        user.setLastLoginAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return issue(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash(request.refreshToken()))
                .orElseThrow(() -> new BadCredentialsException("刷新令牌无效或已失效"));
        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            stored.setRevokedAt(OffsetDateTime.now());
            throw new BadCredentialsException("刷新令牌已过期，请重新登录");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("用户不存在"));
        ensureActive(user);
        stored.setRevokedAt(OffsetDateTime.now());
        return issue(user);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash(request.refreshToken()))
                .ifPresent(token -> token.setRevokedAt(OffsetDateTime.now()));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse me(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BadCredentialsException("用户不存在"));
        UserQuota quota = quotaRepository.findById(userId).orElseGet(() -> UserQuota.builder().userId(userId).build());
        OffsetDateTime today = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        long requests = value(usageRepository.sumRequestsSince(userId, today));
        long tokens = value(usageRepository.sumTokensSince(userId, today));
        return new CurrentUserResponse(user.getId().toString(), user.getUsername(), user.getEmail(),
                user.getRole().name(), user.getStatus().name(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                new CurrentUserResponse.Quota(quota.getDailyRequestLimit(), quota.getDailyTokenLimit(),
                        Boolean.TRUE.equals(quota.getUnlimited()), requests, tokens));
    }

    private AuthResponse issue(User user) {
        String rawRefreshToken = newRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hash(rawRefreshToken))
                .expiresAt(OffsetDateTime.now().plusDays(refreshExpirationDays))
                .build());
        String accessToken = jwtUtil.generateToken(user);
        Instant expiry = Instant.now().plusMillis(jwtUtil.getExpirationMs());
        return new AuthResponse(accessToken, rawRefreshToken, user.getId().toString(), user.getUsername(),
                user.getRole().name(), expiry.toString());
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) throw new AccountDisabledException("账号已被停用");
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("无法处理刷新令牌", error);
        }
    }

    private String normalizeUsername(String username) { return username.trim().toLowerCase(Locale.ROOT); }
    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
    private long value(Long value) { return value == null ? 0 : value; }
}
