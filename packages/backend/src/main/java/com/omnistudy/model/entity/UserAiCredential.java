package com.omnistudy.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_ai_credentials")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAiCredential {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String provider = "dashscope";

    @Column(name = "key_ciphertext", nullable = false)
    private String keyCiphertext;

    @Column(name = "key_iv", nullable = false, length = 64)
    private String keyIv;

    @Column(name = "key_hint", nullable = false, length = 16)
    private String keyHint;

    @Column(name = "fast_vision_model", length = 100)
    private String fastVisionModel;

    @Column(name = "strong_text_model", length = 100)
    private String strongTextModel;

    @Column(name = "last_verified_at")
    private OffsetDateTime lastVerifiedAt;

    @Column(name = "last_test_error", length = 500)
    private String lastTestError;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}
