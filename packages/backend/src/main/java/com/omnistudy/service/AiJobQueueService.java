package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.model.entity.AiJob;
import com.omnistudy.repository.AiJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiJobQueueService {
    private final AiJobRepository repository;
    private final ObjectMapper objectMapper;

    @Value("${app.ai-jobs.max-attempts:3}")
    private int maxAttempts;

    @Transactional
    public void enqueueNote(UUID sessionId, UUID userId, boolean finalize) {
        var payload = objectMapper.createObjectNode().put("sessionId", sessionId.toString());
        repository.enqueueNote(UUID.randomUUID(), userId, "NOTE_GENERATION:" + sessionId,
                payload.toString(), finalize, Math.max(1, maxAttempts));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AiJob> claimNext(String workerId) {
        Optional<AiJob> candidate = repository.findNextForUpdate();
        candidate.ifPresent(job -> {
            job.setStatus("RUNNING");
            job.setAttempts(job.getAttempts() + 1);
            job.setLockedAt(OffsetDateTime.now());
            job.setLockedBy(workerId);
            job.setRerunRequested(false);
            job.setLastError(null);
            job.setUpdatedAt(OffsetDateTime.now());
            repository.save(job);
        });
        return candidate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(UUID jobId) {
        AiJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException("AI job 不存在: " + jobId));
        boolean rerun = Boolean.TRUE.equals(job.getRerunRequested());
        job.setStatus(rerun ? "PENDING" : "SUCCEEDED");
        job.setAvailableAt(OffsetDateTime.now());
        job.setLockedAt(null);
        job.setLockedBy(null);
        job.setRerunRequested(false);
        job.setAttempts(rerun ? 0 : job.getAttempts());
        if (!rerun) job.setFinalizeRequested(false);
        job.setUpdatedAt(OffsetDateTime.now());
        repository.save(job);
        return rerun;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(UUID jobId, Throwable error) {
        AiJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException("AI job 不存在: " + jobId));
        boolean retry = job.getAttempts() < job.getMaxAttempts();
        job.setStatus(retry ? "PENDING" : "FAILED");
        job.setAvailableAt(OffsetDateTime.now().plusSeconds(retryDelaySeconds(job.getAttempts())));
        job.setLockedAt(null);
        job.setLockedBy(null);
        job.setLastError(truncate(error == null ? "未知错误" : error.getMessage(), 4_000));
        job.setUpdatedAt(OffsetDateTime.now());
        repository.save(job);
        return retry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failPermanently(UUID jobId, Throwable error) {
        AiJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalArgumentException("AI job 不存在: " + jobId));
        job.setStatus("FAILED");
        job.setLockedAt(null);
        job.setLockedBy(null);
        job.setLastError(truncate(error == null ? "未知错误" : error.getMessage(), 4_000));
        job.setUpdatedAt(OffsetDateTime.now());
        repository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStale(OffsetDateTime staleBefore) {
        return repository.recoverStale(staleBefore);
    }

    private long retryDelaySeconds(int attempts) {
        return Math.min(300, 15L * (1L << Math.min(4, Math.max(0, attempts - 1))));
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "未知错误";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
