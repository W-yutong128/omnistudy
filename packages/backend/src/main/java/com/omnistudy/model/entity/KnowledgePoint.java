package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "knowledge_points")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KnowledgePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 500)
    private String normalizedName;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String mastery = "陌生的";

    @Column(name = "first_seen")
    @Builder.Default
    private OffsetDateTime firstSeen = OffsetDateTime.now();

    @Column(name = "last_reviewed")
    @Builder.Default
    private OffsetDateTime lastReviewed = OffsetDateTime.now();

    @Column(name = "mastery_score", nullable = false)
    @Builder.Default
    private Double masteryScore = 0.20;

    @Column(name = "next_review_at", nullable = false)
    @Builder.Default
    private OffsetDateTime nextReviewAt = OffsetDateTime.now();

    @Column(name = "review_interval_days", nullable = false)
    @Builder.Default
    private Integer reviewIntervalDays = 1;

    @Column(name = "correct_streak", nullable = false)
    @Builder.Default
    private Integer correctStreak = 0;

    @Column(name = "sources", columnDefinition = "uuid[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<UUID> sources = new java.util.ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
}
