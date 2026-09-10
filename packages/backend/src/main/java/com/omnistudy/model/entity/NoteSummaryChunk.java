package com.omnistudy.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "note_summary_chunks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NoteSummaryChunk {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    @Builder.Default
    private Integer part = 1;

    @Column(name = "t_start", nullable = false)
    private Float tStart;

    @Column(name = "t_end")
    private Float tEnd;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode summaryJson;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
