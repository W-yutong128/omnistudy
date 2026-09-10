package com.omnistudy.repository;

import com.omnistudy.model.entity.Screenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScreenshotRepository extends JpaRepository<Screenshot, UUID> {
    List<Screenshot> findBySessionIdOrderByTAsc(UUID sessionId);
}
