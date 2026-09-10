package com.omnistudy.repository;

import com.omnistudy.model.entity.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {
    boolean existsByUserIdAndOperationKeyAndRequestCountGreaterThan(
            UUID userId, String operationKey, Integer requestCount);

    @Query("select coalesce(sum(u.requestCount), 0) from UsageRecord u where u.userId = :userId and u.createdAt >= :since")
    Long sumRequestsSince(UUID userId, OffsetDateTime since);

    @Query("select coalesce(sum(u.totalTokens), 0) from UsageRecord u where u.userId = :userId and u.createdAt >= :since")
    Long sumTokensSince(UUID userId, OffsetDateTime since);

    @Query("select coalesce(sum(u.requestCount), 0) from UsageRecord u where u.createdAt >= :since")
    Long sumAllRequestsSince(OffsetDateTime since);

    @Query("select coalesce(sum(u.totalTokens), 0) from UsageRecord u where u.createdAt >= :since")
    Long sumAllTokensSince(OffsetDateTime since);

    @Query("select coalesce(sum(u.estimatedCostMicros), 0) from UsageRecord u where u.createdAt >= :since")
    Long sumAllCostSince(OffsetDateTime since);

    @Query("select u.feature, coalesce(sum(u.requestCount), 0), coalesce(sum(u.totalTokens), 0), " +
            "coalesce(sum(u.estimatedCostMicros), 0) from UsageRecord u " +
            "where u.createdAt >= :since group by u.feature order by sum(u.estimatedCostMicros) desc")
    List<Object[]> summarizeByFeatureSince(OffsetDateTime since);

    @Query("select u.feature, coalesce(sum(u.requestCount), 0), coalesce(sum(u.inputTokens), 0), " +
            "coalesce(sum(u.outputTokens), 0), coalesce(sum(u.cachedTokens), 0), " +
            "coalesce(sum(u.totalTokens), 0), coalesce(sum(u.estimatedCostMicros), 0), " +
            "coalesce(sum(case when u.status = 'SUCCEEDED' then 1 else 0 end), 0), " +
            "coalesce(sum(case when u.status = 'FAILED' then 1 else 0 end), 0) " +
            "from UsageRecord u where u.userId = :userId and u.createdAt >= :since " +
            "group by u.feature order by sum(u.totalTokens) desc")
    List<Object[]> summarizeUserByFeatureSince(UUID userId, OffsetDateTime since);

    List<UsageRecord> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
}
