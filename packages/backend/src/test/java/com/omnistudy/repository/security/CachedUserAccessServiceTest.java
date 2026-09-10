package com.omnistudy.repository.security;

import com.omnistudy.model.entity.User;
import com.omnistudy.model.entity.UserRole;
import com.omnistudy.model.entity.UserStatus;
import com.omnistudy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CachedUserAccessServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final CachedUserAccessService service = new CachedUserAccessService(users, redis);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "redisEnabled", true);
        ReflectionTestUtils.setField(service, "keyPrefix", "test");
        ReflectionTestUtils.setField(service, "ttlSeconds", 60L);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void servesAValidPrincipalFromRedisWithoutQueryingPostgres() {
        UUID userId = UUID.randomUUID();
        when(values.get("test:auth:principal:" + userId)).thenReturn("ADMIN:ACTIVE");

        CachedUserAccessService.UserAccess access = service.find(userId).orElseThrow();

        assertEquals(UserRole.ADMIN, access.role());
        assertEquals(UserStatus.ACTIVE, access.status());
        verifyNoInteractions(users);
    }

    @Test
    void cachesPostgresResultOnAMissAndSupportsInvalidation() {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).role(UserRole.USER).status(UserStatus.ACTIVE).build()));

        service.find(userId);
        service.invalidateAfterCommit(userId);

        verify(values).set(eq("test:auth:principal:" + userId), eq("USER:ACTIVE"), eq(Duration.ofSeconds(60)));
        verify(redis).delete("test:auth:principal:" + userId);
    }
}
