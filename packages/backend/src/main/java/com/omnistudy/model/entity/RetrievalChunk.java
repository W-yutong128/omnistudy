package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "retrieval_chunks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetrievalChunk {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "session_id") private UUID sessionId;
    @Column(name = "note_id") private UUID noteId;
    private Integer part;
    @Column(name = "start_time") private Double startTime;
    @Column(name = "end_time") private Double endTime;
    @Column(name = "content_type", nullable = false, length = 40) private String contentType;
    @Column(nullable = false, columnDefinition = "text") private String title;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(nullable = false, columnDefinition = "real[]") private Float[] embedding;
    @Column(name = "created_at", nullable = false) @Builder.Default private OffsetDateTime createdAt = OffsetDateTime.now();
}
