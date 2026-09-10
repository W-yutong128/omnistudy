package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column
    private Float t;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "note_id")
    private UUID noteId;

    @Column(name = "knowledge_point_id")
    private UUID knowledgePointId;

    @Column(name = "bank_question_id")
    private UUID bankQuestionId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String origin = "course_intercept";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prompt_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode promptJson;

    @Column(nullable = false)
    @Builder.Default
    private Integer difficulty = 2;

    @Column(nullable = false)
    @Builder.Default
    private Integer part = 1;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private StudySession session;
}
