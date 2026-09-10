package com.omnistudy.repository;

import com.omnistudy.model.entity.KnowledgePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgePointRepository extends JpaRepository<KnowledgePoint, UUID> {

    List<KnowledgePoint> findByUserIdOrderByLastReviewedDesc(UUID userId);

    List<KnowledgePoint> findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(UUID userId, java.time.OffsetDateTime now);

    Optional<KnowledgePoint> findByUserIdAndNormalizedName(UUID userId, String normalizedName);

    @Query("""
            SELECT kp FROM KnowledgePoint kp
            WHERE kp.userId = :userId
              AND kp.mastery != '掌握的'
              AND EXISTS (
                  SELECT a.id FROM Attempt a, Question q
                  WHERE a.questionId = q.id
                    AND q.knowledgePointId = kp.id
              )
            ORDER BY kp.lastReviewed ASC
            """)
    List<KnowledgePoint> findWeakPointsByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT kp FROM KnowledgePoint kp
            WHERE kp.userId = :userId
              AND kp.mastery != '掌握的'
              AND EXISTS (
                  SELECT a.id FROM Attempt a, Question q
                  WHERE a.questionId = q.id
                    AND q.knowledgePointId = kp.id
                    AND q.sessionId = :sessionId
              )
            ORDER BY kp.lastReviewed ASC
            """)
    List<KnowledgePoint> findWeakPointsByUserIdAndSessionId(
            @Param("userId") UUID userId, @Param("sessionId") UUID sessionId);

    @Query("SELECT COUNT(kp) FROM KnowledgePoint kp WHERE kp.userId = :userId")
    int countByUserId(@Param("userId") UUID userId);
}
