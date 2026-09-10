package com.omnistudy.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RateLimitServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RateLimitService service = new RateLimitService(redis, new SimpleMeterRegistry());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "redisEnabled", false);
        ReflectionTestUtils.setField(service, "keyPrefix", "test");
        service.useClock(Clock.fixed(Instant.parse("2026-09-09T00:00:10Z"), ZoneOffset.UTC));
    }

    @Test
    void localFallbackRejectsRequestsPastTheLimitAndIsolatesSubjects() {
        assertTrue(service.check("login", "client-a", 2, Duration.ofMinutes(1)).allowed());
        assertTrue(service.check("login", "client-a", 2, Duration.ofMinutes(1)).allowed());
        RateLimitService.Decision rejected = service.check("login", "client-a", 2, Duration.ofMinutes(1));

        assertFalse(rejected.allowed());
        assertEquals(0, rejected.remaining());
        assertEquals(50, rejected.retryAfterSeconds());
        assertTrue(service.check("login", "client-b", 2, Duration.ofMinutes(1)).allowed());
        verifyNoInteractions(redis);
    }

    @Test
    void startsANewCounterInTheNextFixedWindow() {
        assertTrue(service.check("ai", "user", 1, Duration.ofMinutes(1)).allowed());
        assertFalse(service.check("ai", "user", 1, Duration.ofMinutes(1)).allowed());

        service.useClock(Clock.fixed(Instant.parse("2026-09-09T00:01:01Z"), ZoneOffset.UTC));

        assertTrue(service.check("ai", "user", 1, Duration.ofMinutes(1)).allowed());
    }
}
