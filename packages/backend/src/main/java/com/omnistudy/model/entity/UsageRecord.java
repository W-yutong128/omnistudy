package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UsageRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "trace_id")
    private UUID traceId;
    @Column(nullable = false, length = 40)
    @Builder.Default
    private String feature = "AGENT";
    @Column(name = "operation_key", length = 120)
    private String operationKey;
    @Column(name = "request_count", nullable = false)
    @Builder.Default
    private Integer requestCount = 1;
    @Column(name = "input_tokens", nullable = false)
    @Builder.Default
    private Integer inputTokens = 0;
    @Column(name = "output_tokens", nullable = false)
    @Builder.Default
    private Integer outputTokens = 0;
    @Column(name = "cached_tokens", nullable = false)
    @Builder.Default
    private Integer cachedTokens = 0;
    @Column(name = "total_tokens", nullable = false)
    @Builder.Default
    private Long totalTokens = 0L;
    @Column(name = "estimated_cost_micros", nullable = false)
    @Builder.Default
    private Long estimatedCostMicros = 0L;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
