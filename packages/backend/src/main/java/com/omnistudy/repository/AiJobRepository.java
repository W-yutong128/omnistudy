package com.omnistudy.repository;

import com.omnistudy.model.entity.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AiJobRepository extends JpaRepository<AiJob, UUID> {

    long countByStatus(String status);

    Optional<AiJob> findFirstByUserIdAndOperationKeyOrderByCreatedAtDesc(UUID userId, String operationKey);

    @Modifying
    @Query(value = """
            INSERT INTO ai_jobs(id, user_id, job_type, operation_key, payload_json, status,
                                attempts, max_attempts, finalize_requested, rerun_requested,
                                available_at, created_at, updated_at)
            VALUES (:id, :userId, 'NOTE_GENERATION', :operationKey, CAST(:payload AS jsonb),
                    'PENDING', 0, :maxAttempts, :finalizeRequested, FALSE, NOW(), NOW(), NOW())
            ON CONFLICT (job_type, operation_key) WHERE status IN ('PENDING', 'RUNNING')
            DO UPDATE SET
                finalize_requested = ai_jobs.finalize_requested OR EXCLUDED.finalize_requested,
                rerun_requested = ai_jobs.rerun_requested OR ai_jobs.status = 'RUNNING',
                updated_at = NOW()
            """, nativeQuery = true)
    int enqueueNote(@Param("id") UUID id,
                    @Param("userId") UUID userId,
                    @Param("operationKey") String operationKey,
                    @Param("payload") String payload,
                    @Param("finalizeRequested") boolean finalizeRequested,
                    @Param("maxAttempts") int maxAttempts);

    @Query(value = """
            SELECT * FROM ai_jobs
            WHERE status = 'PENDING' AND available_at <= NOW()
            ORDER BY available_at, created_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AiJob> findNextForUpdate();

    @Query(value = "SELECT * FROM ai_jobs WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<AiJob> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE ai_jobs
            SET status = 'PENDING', locked_at = NULL, locked_by = NULL,
                available_at = NOW(), rerun_requested = TRUE,
                last_error = CONCAT(COALESCE(last_error, ''), CASE WHEN last_error IS NULL THEN '' ELSE E'\\n' END,
                                    'Recovered stale RUNNING job'), updated_at = NOW()
            WHERE status = 'RUNNING' AND locked_at < :staleBefore
            """, nativeQuery = true)
    int recoverStale(@Param("staleBefore") OffsetDateTime staleBefore);
}
