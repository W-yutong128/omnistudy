package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "notes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", unique = true)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    @Builder.Default
    private String title = "未命名笔记";

    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String markdown = "";

    @Column(name = "course_name", length = 500)
    private String courseName;

    @Column(name = "chapter_name", length = 500)
    private String chapterName;

    @Column(name = "source_title", length = 500)
    private String sourceTitle;

    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    @Column(name = "source_timestamp")
    private Integer sourceTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private JsonNode tags = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "linked_note_ids", columnDefinition = "uuid[]")
    @Builder.Default
    private List<UUID> linkedNoteIds = new ArrayList<>();

    @Column(name = "content_status", nullable = false, length = 30)
    @Builder.Default
    private String contentStatus = "draft";

    @Column(name = "mastery_status", nullable = false, length = 30)
    @Builder.Default
    private String masteryStatus = "unlearned";

    @Column(nullable = false)
    @Builder.Default
    private Boolean inbox = false;

    @Column(name = "next_review_at")
    private OffsetDateTime nextReviewAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "study_materials", nullable = false, columnDefinition = "jsonb")
    private JsonNode studyMaterials;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "jsonb")
    private JsonNode contentJson;

    @Column(nullable = false, length = 20)
    private String status = "generating";

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", insertable = false, updatable = false)
    private StudySession session;
}
