package com.omnistudy.repository;

import com.omnistudy.model.entity.RetrievalChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetrievalChunkRepository extends JpaRepository<RetrievalChunk, UUID> {
    Optional<RetrievalChunk> findByUserIdAndContentHash(UUID userId, String contentHash);
    void deleteByNoteId(UUID noteId);
    long countByUserId(UUID userId);

    @Query(value = """
            select c.* from retrieval_chunks c
            where c.user_id = :userId
              and (cast(:sessionId as uuid) is null or c.session_id = :sessionId)
              and (c.search_vector @@ plainto_tsquery('simple', :query)
                   or lower(c.title || ' ' || c.content) like lower('%' || :query || '%'))
            order by ts_rank_cd(c.search_vector, plainto_tsquery('simple', :query)) desc,
                     c.created_at desc
            """, nativeQuery = true)
    List<RetrievalChunk> lexicalCandidates(@Param("userId") UUID userId,
                                           @Param("sessionId") UUID sessionId,
                                           @Param("query") String query,
                                           Pageable pageable);

    @Query("""
            select c from RetrievalChunk c
            where c.userId = :userId
              and (:sessionId is null or c.sessionId = :sessionId)
            order by c.createdAt desc
            """)
    List<RetrievalChunk> candidates(@Param("userId") UUID userId,
                                    @Param("sessionId") UUID sessionId,
                                    Pageable pageable);
}
