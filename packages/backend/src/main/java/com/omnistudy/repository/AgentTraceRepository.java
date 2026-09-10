package com.omnistudy.repository;

import com.omnistudy.model.entity.AgentTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentTraceRepository extends JpaRepository<AgentTrace, UUID> {
    List<AgentTrace> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
}
