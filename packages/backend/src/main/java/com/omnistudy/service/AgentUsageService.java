package com.omnistudy.service;

import com.omnistudy.exception.QuotaExceededException;
import com.omnistudy.model.entity.AgentTrace;
import com.omnistudy.model.entity.UsageRecord;
import com.omnistudy.model.entity.UserQuota;
import com.omnistudy.model.dto.UserUsageOverviewResponse;
import com.omnistudy.repository.UsageRecordRepository;
import com.omnistudy.repository.UserQuotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentUsageService {
    private final UserQuotaRepository quotaRepository;
    private final UsageRecordRepository usageRepository;

    @Value("${app.usage.input-yuan-per-million:0.15}")
    private double inputYuanPerMillion;
    @Value("${app.usage.output-yuan-per-million:1.5}")
    private double outputYuanPerMillion;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID begin(UUID userId, int estimatedTokens) {
        return begin(userId, estimatedTokens, "AGENT");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID begin(UUID userId, int estimatedTokens, String feature) {
        return begin(userId, estimatedTokens, feature, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID beginOperation(UUID userId, int estimatedTokens, String feature, String operationKey) {
        if (operationKey == null || operationKey.isBlank()) {
            throw new IllegalArgumentException("operationKey 不能为空");
        }
        return begin(userId, estimatedTokens, feature, normalizeOperationKey(operationKey), null);
    }

    /**
     * Reserves Token quota for an internal model call that belongs to an already-counted user
     * operation. Compaction and memory maintenance must contribute real Token/cost without
     * consuming another daily request.
     */
    public UUID beginInternal(UUID userId, int estimatedTokens, String feature) {
        return begin(userId, estimatedTokens, feature, null, false);
    }

    private UUID begin(UUID userId, int estimatedTokens, String feature, String operationKey) {
        return begin(userId, estimatedTokens, feature, operationKey, null);
    }

    private UUID begin(UUID userId, int estimatedTokens, String feature, String operationKey,
                       Boolean forceCountRequest) {
        UserQuota quota = quotaRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> quotaRepository.save(UserQuota.builder().userId(userId).build()));
        OffsetDateTime today = today();
        long requests = value(usageRepository.sumRequestsSince(userId, today));
        long tokens = value(usageRepository.sumTokensSince(userId, today));
        long reserved = Math.max(1, estimatedTokens);
        boolean countRequest = forceCountRequest != null ? forceCountRequest
                : operationKey == null || !usageRepository
                .existsByUserIdAndOperationKeyAndRequestCountGreaterThan(userId, operationKey, 0);
        if (!Boolean.TRUE.equals(quota.getUnlimited())) {
            if (countRequest && requests >= quota.getDailyRequestLimit()) {
                throw new QuotaExceededException("今日 AI 调用次数已用完，请明天再试或联系管理员调整额度");
            }
            if (tokens + reserved > quota.getDailyTokenLimit()) {
                throw new QuotaExceededException("今日 AI Token 额度不足，请明天再试或联系管理员调整额度");
            }
        }
        return usageRepository.save(UsageRecord.builder()
                .userId(userId).feature(normalizeFeature(feature)).operationKey(operationKey)
                .requestCount(countRequest ? 1 : 0).totalTokens(reserved).status("RUNNING").build()).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID reservationId, AgentTrace trace, boolean requestSucceeded) {
        UsageRecord usage = usageRepository.findById(reservationId).orElse(null);
        if (usage == null) return;
        if (trace != null) {
            int input = safe(trace.getInputTokens());
            int output = safe(trace.getOutputTokens());
            int cached = safe(trace.getCachedTokens());
            usage.setTraceId(trace.getId());
            usage.setInputTokens(input);
            usage.setOutputTokens(output);
            usage.setCachedTokens(cached);
            if (input + output > 0) usage.setTotalTokens((long) input + output);
            usage.setEstimatedCostMicros(Math.max(0, Math.round(input * inputYuanPerMillion + output * outputYuanPerMillion)));
            usage.setStatus(trace.getState() != null && trace.getState().contains("FALLBACK") ? "FALLBACK" :
                    requestSucceeded ? "SUCCEEDED" : "FAILED");
        } else {
            usage.setStatus(requestSucceeded ? "SUCCEEDED" : "FAILED");
        }
        usage.setUpdatedAt(OffsetDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID reservationId, int inputTokens, int outputTokens, int cachedTokens,
                       boolean requestSucceeded) {
        UsageRecord usage = usageRepository.findById(reservationId).orElse(null);
        if (usage == null) return;
        int input = Math.max(0, inputTokens);
        int output = Math.max(0, outputTokens);
        int cached = Math.max(0, cachedTokens);
        usage.setInputTokens(input);
        usage.setOutputTokens(output);
        usage.setCachedTokens(cached);
        if (input + output > 0) usage.setTotalTokens((long) input + output);
        usage.setEstimatedCostMicros(Math.max(0, Math.round(
                input * inputYuanPerMillion + output * outputYuanPerMillion)));
        usage.setStatus(requestSucceeded ? "SUCCEEDED" : "FAILED");
        usage.setUpdatedAt(OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public UserUsageOverviewResponse todayOverview(UUID userId) {
        OffsetDateTime since = today();
        List<UserUsageOverviewResponse.FeatureUsage> features = usageRepository
                .summarizeUserByFeatureSince(userId, since).stream()
                .map(this::featureUsage)
                .toList();
        return new UserUsageOverviewResponse(
                since.toString(),
                features.stream().mapToLong(UserUsageOverviewResponse.FeatureUsage::requests).sum(),
                features.stream().mapToLong(UserUsageOverviewResponse.FeatureUsage::inputTokens).sum(),
                features.stream().mapToLong(UserUsageOverviewResponse.FeatureUsage::outputTokens).sum(),
                features.stream().mapToLong(UserUsageOverviewResponse.FeatureUsage::cachedTokens).sum(),
                features.stream().mapToLong(UserUsageOverviewResponse.FeatureUsage::totalTokens).sum(),
                features.stream().mapToLong(UserUsageOverviewResponse.FeatureUsage::estimatedCostMicros).sum(),
                features.stream().mapToLong(UserUsageOverviewResponse.FeatureUsage::succeeded).sum(),
                features.stream().mapToLong(UserUsageOverviewResponse.FeatureUsage::failed).sum(),
                features);
    }

    private UserUsageOverviewResponse.FeatureUsage featureUsage(Object[] row) {
        return new UserUsageOverviewResponse.FeatureUsage(
                String.valueOf(row[0]), number(row[1]), number(row[2]), number(row[3]),
                number(row[4]), number(row[5]), number(row[6]), number(row[7]), number(row[8]));
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private OffsetDateTime today() {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone).atStartOfDay(zone).toOffsetDateTime();
    }
    private long value(Long value) { return value == null ? 0 : value; }
    private int safe(Integer value) { return value == null ? 0 : value; }
    private String normalizeFeature(String feature) {
        if (feature == null || feature.isBlank()) return "OTHER";
        String normalized = feature.trim().toUpperCase(java.util.Locale.ROOT);
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40);
    }
    private String normalizeOperationKey(String operationKey) {
        String normalized = operationKey.trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }
}
