package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "attempt_num", nullable = false)
    private Integer attemptNum = 1;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evaluation_json", columnDefinition = "jsonb")
    private JsonNode evaluationJson;

    @Column(nullable = false, length = 20)
    private String decided; // pass / follow_up / support

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private Question question;
}
