package com.omnistudy.repository;

import com.omnistudy.model.entity.QuestionBankItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionBankRepository extends JpaRepository<QuestionBankItem, UUID> {
    Optional<QuestionBankItem> findBySourceDocumentAndSourceQuestionNo(String sourceDocument, Integer sourceQuestionNo);

    @Query(value = """
        SELECT qb.* FROM question_bank qb
        WHERE qb.review_status = 'ready'
          AND (:subject = '' OR qb.subject = :subject)
          AND (:query = '' OR
               to_tsvector('simple', coalesce(qb.question_text, '') || ' ' || coalesce(qb.explanation, '') || ' ' ||
                   coalesce(qb.subject, '') || ' ' || coalesce(qb.knowledge_tags::text, ''))
               @@ plainto_tsquery('simple', :query)
               OR lower(qb.question_text) LIKE lower('%' || :query || '%')
               OR lower(qb.knowledge_tags::text) LIKE lower('%' || :query || '%'))
          AND NOT EXISTS (
              SELECT 1 FROM questions q JOIN attempts a ON a.question_id = q.id
              WHERE q.bank_question_id = qb.id AND q.user_id = :userId)
        ORDER BY qb.source_year DESC, qb.source_question_no ASC
        """, nativeQuery = true)
    List<QuestionBankItem> search(@Param("query") String query,
                                  @Param("subject") String subject,
                                  @Param("userId") UUID userId,
                                  Pageable pageable);
}
