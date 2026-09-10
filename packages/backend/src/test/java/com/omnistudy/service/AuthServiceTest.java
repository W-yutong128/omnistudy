package com.omnistudy.service;

import com.omnistudy.exception.AccountDisabledException;
import com.omnistudy.model.dto.AuthRequest;
import com.omnistudy.model.dto.RegisterRequest;
import com.omnistudy.model.entity.RefreshToken;
import com.omnistudy.model.entity.User;
import com.omnistudy.model.entity.UserRole;
import com.omnistudy.model.entity.UserStatus;
import com.omnistudy.repository.*;
import com.omnistudy.repository.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserQuotaRepository quotas = mock(UserQuotaRepository.class);
    private final UsageRecordRepository usage = mock(UsageRecordRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AuthService service = new AuthService(users, quotas, usage, refreshTokens, jwt, passwords);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshExpirationDays", 30L);
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(call -> call.getArgument(0));
        when(jwt.generateToken(any(User.class))).thenReturn("access-token");
        when(jwt.getExpirationMs()).thenReturn(60_000L);
    }

    @Test
    void registersARegularUserAndCreatesQuota() {
        when(passwords.encode("password123")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        var response = service.register(new RegisterRequest("New_User", "USER@example.com", "password123"));

        assertEquals("new_user", response.username());
        assertEquals("USER", response.role());
        assertNotNull(response.refreshToken());
        verify(quotas).save(argThat(quota -> quota.getUserId() != null));
    }

    @Test
    void disabledUserCannotLogin() {
        User user = User.builder().id(UUID.randomUUID()).username("blocked").passwordHash("hash")
                .role(UserRole.USER).status(UserStatus.DISABLED).build();
        when(users.findByUsername("blocked")).thenReturn(Optional.of(user));
        when(passwords.matches("password123", "hash")).thenReturn(true);

        assertThrows(AccountDisabledException.class,
                () -> service.login(new AuthRequest("blocked", "password123")));
        verifyNoInteractions(refreshTokens);
    }
}
