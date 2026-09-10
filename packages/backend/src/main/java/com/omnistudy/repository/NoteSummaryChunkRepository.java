package com.omnistudy.repository;

import com.omnistudy.model.entity.NoteSummaryChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteSummaryChunkRepository extends JpaRepository<NoteSummaryChunk, UUID> {
    @Query("SELECT n FROM NoteSummaryChunk n WHERE n.sessionId = :sessionId ORDER BY n.part ASC, n.tStart ASC")
    List<NoteSummaryChunk> listForSession(@Param("sessionId") UUID sessionId);
    Optional<NoteSummaryChunk> findBySessionIdAndContentHash(UUID sessionId, String contentHash);
}
