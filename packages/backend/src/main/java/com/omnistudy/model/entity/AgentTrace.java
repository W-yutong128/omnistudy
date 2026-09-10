package com.omnistudy.model.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_traces")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentTrace {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "session_id") private UUID sessionId;
    @Column(nullable = false, length = 40) private String state;
    @Column(name = "user_message", nullable = false) private String userMessage;
    @Column(name = "tool_name", length = 80) private String toolName;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "tool_args", nullable = false, columnDefinition = "jsonb") private JsonNode toolArgs;
    @Column(columnDefinition = "text") private String observation;
    @Column(name = "response_text", columnDefinition = "text") private String responseText;
    @Column(name = "model_name", length = 120) private String modelName;
    @Column(name = "prompt_version", nullable = false, length = 40) private String promptVersion;
    @Column(name = "skill_name", length = 80) private String skillName;
    @Column(name = "skill_version", length = 20) private String skillVersion;
    @Column(nullable = false, length = 40) @Builder.Default private String framework = "custom-harness";
    @Column(name = "input_tokens", nullable = false) @Builder.Default private Integer inputTokens = 0;
    @Column(name = "output_tokens", nullable = false) @Builder.Default private Integer outputTokens = 0;
    @Column(name = "cached_tokens", nullable = false) @Builder.Default private Integer cachedTokens = 0;
    @Column(name = "step_count", nullable = false) private Integer stepCount;
    @Column(name = "latency_ms", nullable = false) private Long latencyMs;
    @Column(nullable = false) private Boolean success;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @Column(name = "created_at", nullable = false) @Builder.Default private OffsetDateTime createdAt = OffsetDateTime.now();
}
