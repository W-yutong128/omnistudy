package com.omnistudy.repository;

import com.omnistudy.model.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {
    List<Question> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    List<Question> findBySessionIdAndOriginOrderByCreatedAtAsc(UUID sessionId, String origin);
    List<Question> findByNoteIdOrderByCreatedAtAsc(UUID noteId);
    int countBySessionId(UUID sessionId);
    int countBySessionIdAndOrigin(UUID sessionId, String origin);
}
