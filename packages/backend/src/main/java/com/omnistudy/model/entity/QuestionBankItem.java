package com.omnistudy.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "question_bank")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionBankItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;
    @Column(name = "source_document", nullable = false, length = 500)
    private String sourceDocument;
    @Column(name = "source_year")
    private Integer sourceYear;
    @Column(name = "source_exam", length = 500)
    private String sourceExam;
    @Column(name = "source_question_no", nullable = false)
    private Integer sourceQuestionNo;
    @Column(name = "source_page")
    private Integer sourcePage;
    @Column(nullable = false, length = 50)
    private String subject;
    @Column(name = "question_type", nullable = false, length = 30)
    @Builder.Default private String questionType = "single_choice";
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", nullable = false, columnDefinition = "jsonb")
    private JsonNode optionsJson;
    @Column(name = "correct_option_id", nullable = false, length = 10)
    private String correctOptionId;
    @Column(columnDefinition = "TEXT")
    private String explanation;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "knowledge_tags", nullable = false, columnDefinition = "jsonb")
    private JsonNode knowledgeTags;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "asset_paths", nullable = false, columnDefinition = "jsonb")
    private JsonNode assetPaths;
    @Column(nullable = false)
    @Builder.Default private Integer difficulty = 2;
    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;
    @Column(name = "license_status", nullable = false, length = 30)
    @Builder.Default private String licenseStatus = "unverified";
    @Column(name = "review_status", nullable = false, length = 30)
    @Builder.Default private String reviewStatus = "ready";
    @Column(name = "created_at", nullable = false)
    @Builder.Default private OffsetDateTime createdAt = OffsetDateTime.now();
}

