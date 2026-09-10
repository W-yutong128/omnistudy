package com.omnistudy.repository;

import com.omnistudy.model.entity.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<StudySession, UUID> {
    List<StudySession> findByUserIdOrderByStartedAtDesc(UUID userId);
}
