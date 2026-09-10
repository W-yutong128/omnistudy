package com.omnistudy.repository.security;

import com.omnistudy.model.entity.UserRole;
import com.omnistudy.model.entity.UserStatus;
import com.omnistudy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class CachedUserAccessService {
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final AtomicBoolean redisFailureLogged = new AtomicBoolean();

    @Value("${app.redis.enabled:false}")
    private boolean redisEnabled;

    @Value("${app.redis.key-prefix:omnistudy}")
    private String keyPrefix;

    @Value("${app.auth.principal-cache-ttl-seconds:60}")
    private long ttlSeconds;

    public Optional<UserAccess> find(UUID userId) {
        String key = key(userId);
        if (redisEnabled) {
            try {
                UserAccess cached = decode(redisTemplate.opsForValue().get(key));
                if (cached != null) {
                    redisFailureLogged.set(false);
                    return Optional.of(cached);
                }
            } catch (RuntimeException error) {
                logRedisFailure(error);
            }
        }

        Optional<UserAccess> result = userRepository.findById(userId)
                .map(user -> new UserAccess(user.getRole(), user.getStatus()));
        if (redisEnabled && result.isPresent()) {
            try {
                UserAccess access = result.get();
                redisTemplate.opsForValue().set(key, access.role().name() + ":" + access.status().name(),
                        Duration.ofSeconds(Math.max(1, ttlSeconds)));
                redisFailureLogged.set(false);
            } catch (RuntimeException error) {
                logRedisFailure(error);
            }
        }
        return result;
    }

    public void invalidate(UUID userId) {
        if (!redisEnabled) return;
        try {
            redisTemplate.delete(key(userId));
        } catch (RuntimeException error) {
            logRedisFailure(error);
        }
    }

    public void invalidateAfterCommit(UUID userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate(userId);
                }
            });
            return;
        }
        invalidate(userId);
    }

    private UserAccess decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = value.split(":", 2);
            return new UserAccess(UserRole.valueOf(parts[0]), UserStatus.valueOf(parts[1]));
        } catch (RuntimeException malformed) {
            log.warn("忽略格式错误的用户权限缓存值");
            return null;
        }
    }

    private void logRedisFailure(RuntimeException error) {
        if (redisFailureLogged.compareAndSet(false, true)) {
            log.warn("Redis 用户权限缓存暂时不可用，回源 PostgreSQL: {}", error.getMessage());
        }
    }

    private String key(UUID userId) {
        return keyPrefix + ":auth:principal:" + userId;
    }

    public record UserAccess(UserRole role, UserStatus status) {}
}
