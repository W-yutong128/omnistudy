package com.omnistudy.repository;

import com.omnistudy.model.entity.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttemptRepository extends JpaRepository<Attempt, UUID> {
    List<Attempt> findByQuestionIdOrderByAttemptNumAsc(UUID questionId);
    int countByQuestionId(UUID questionId);
}
