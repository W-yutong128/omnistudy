package com.omnistudy.service;

import com.omnistudy.model.dto.AgentTraceResponse;
import com.omnistudy.model.entity.AgentTrace;
import com.omnistudy.repository.AgentTraceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentTraceService {
    private final AgentTraceRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentTrace save(AgentTrace trace) {
        return repository.save(trace);
    }

    @Transactional(readOnly = true)
    public List<AgentTraceResponse> list(UUID userId) {
        return repository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream().map(trace -> new AgentTraceResponse(
                trace.getId().toString(), trace.getState(), trace.getToolName(), trace.getSkillName(),
                trace.getModelName(), trace.getPromptVersion(), trace.getFramework(), trace.getInputTokens(),
                trace.getOutputTokens(), trace.getCachedTokens(), trace.getStepCount(), trace.getLatencyMs(),
                trace.getSuccess(), trace.getErrorMessage(), trace.getCreatedAt().toString())).toList();
    }
}
