package com.omnistudy.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, LocalWindow> localWindows = new ConcurrentHashMap<>();
    private final AtomicBoolean redisFailureLogged = new AtomicBoolean();
    private final AtomicLong cleanupCounter = new AtomicLong();
    private Clock clock = Clock.systemUTC();

    @Value("${app.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${app.redis.key-prefix:omnistudy}")
    private String keyPrefix;

    public Decision check(String bucket, String subject, int limit, Duration window) {
        if (limit <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("限流阈值和时间窗口必须大于 0");
        }
        long now = clock.millis();
        long windowMillis = window.toMillis();
        long windowStart = now / windowMillis;
        long retryAfterMillis = windowMillis - now % windowMillis;
        String key = keyPrefix + ":rate:" + bucket + ":" + hash(subject) + ":" + windowStart;

        if (redisEnabled) {
            try {
                Long count = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(windowMillis + 1_000));
                if (count != null) {
                    redisFailureLogged.set(false);
                    return decision(count, limit, retryAfterMillis, "redis");
                }
            } catch (RuntimeException error) {
                if (redisFailureLogged.compareAndSet(false, true)) {
                    log.warn("Redis 限流暂时不可用，降级为实例内限流: {}", error.getMessage());
                }
            }
        }

        LocalWindow state = localWindows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAtMillis <= now) {
                return new LocalWindow(1, now + retryAfterMillis);
            }
            return new LocalWindow(existing.count + 1, existing.expiresAtMillis);
        });
        cleanupExpired(now);
        return decision(state.count, limit, Math.max(1, state.expiresAtMillis - now), "local");
    }

    private Decision decision(long count, int limit, long retryAfterMillis, String backend) {
        boolean allowed = count <= limit;
        meterRegistry.counter("omnistudy.rate_limit.decisions", "backend", backend, "result",
                allowed ? "allowed" : "rejected").increment();
        return new Decision(allowed, Math.max(0, limit - count), Math.max(1, (retryAfterMillis + 999) / 1_000));
    }

    private void cleanupExpired(long now) {
        if (cleanupCounter.incrementAndGet() % 256 == 0) {
            localWindows.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis <= now);
        }
    }

    private String hash(String subject) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(subject.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (Exception error) {
            throw new IllegalStateException("无法生成限流键", error);
        }
    }

    void useClock(Clock clock) {
        this.clock = clock;
    }

    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {}
    private record LocalWindow(long count, long expiresAtMillis) {}
}
