package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;
import java.time.OffsetDateTime;

@Entity
@Table(name = "subtitle_chunks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SubtitleChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "t_start", nullable = false)
    private Float tStart;

    @Column(name = "t_end")
    private Float tEnd;

    @Column(nullable = false)
    @Builder.Default
    private Integer part = 1;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private StudySession session;
}
