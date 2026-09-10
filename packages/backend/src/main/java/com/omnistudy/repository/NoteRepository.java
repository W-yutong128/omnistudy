package com.omnistudy.repository;

import com.omnistudy.model.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {
    Optional<Note> findBySessionId(UUID sessionId);
    Optional<Note> findByIdAndUserId(UUID id, UUID userId);
    List<Note> findByUserIdOrderByUpdatedAtDesc(UUID userId);
    List<Note> findByUserIdAndInboxTrueOrderByUpdatedAtDesc(UUID userId);
    List<Note> findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(UUID userId, OffsetDateTime now);

    @org.springframework.data.jpa.repository.Query("""
        select n from Note n where n.userId = :userId and (
        lower(n.title) like lower(concat('%', :query, '%')) or
        lower(n.markdown) like lower(concat('%', :query, '%')) or
        lower(coalesce(n.courseName, '')) like lower(concat('%', :query, '%')) or
        lower(coalesce(n.chapterName, '')) like lower(concat('%', :query, '%')))
        order by n.updatedAt desc
        """)
    List<Note> search(UUID userId, String query);
}
