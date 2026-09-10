package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_quotas")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserQuota {
    @Id @Column(name = "user_id")
    private UUID userId;
    @Column(name = "daily_request_limit", nullable = false)
    @Builder.Default
    private Integer dailyRequestLimit = Integer.MAX_VALUE;
    @Column(name = "daily_token_limit", nullable = false)
    @Builder.Default
    private Long dailyTokenLimit = 200_000L;
    @Column(nullable = false)
    @Builder.Default
    private Boolean unlimited = false;
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
