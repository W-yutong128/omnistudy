package com.omnistudy.repository;

import com.omnistudy.model.entity.SubtitleChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Modifying;

@Repository
public interface SubtitleChunkRepository extends JpaRepository<SubtitleChunk, UUID> {
    @Query("SELECT s FROM SubtitleChunk s WHERE s.sessionId = :sessionId ORDER BY s.tStart ASC")
    List<SubtitleChunk> findBySessionIdOrderByTStartAsc(@Param("sessionId") UUID sessionId);

    // Spring Data 会把派生查询中的 TStart 误解析为属性 "TStart"，因此分页版本也必须显式写 JPQL。
    @Query("SELECT s FROM SubtitleChunk s WHERE s.sessionId = :sessionId ORDER BY s.tStart ASC")
    List<SubtitleChunk> findBySessionIdOrderByTStartAsc(@Param("sessionId") UUID sessionId, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM SubtitleChunk s " +
            "WHERE s.sessionId = :sessionId AND s.tStart = :tStart AND s.text = :text")
    boolean existsCapturedChunk(@Param("sessionId") UUID sessionId,
                                @Param("tStart") Float tStart,
                                @Param("text") String text);

    @Modifying
    @Query("DELETE FROM SubtitleChunk s WHERE s.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") OffsetDateTime cutoff);
}
