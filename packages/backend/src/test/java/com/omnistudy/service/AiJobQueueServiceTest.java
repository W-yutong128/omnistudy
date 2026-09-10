package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.model.entity.AiJob;
import com.omnistudy.repository.AiJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiJobQueueServiceTest {
    private final AiJobRepository repository = mock(AiJobRepository.class);
    private final AiJobQueueService service = new AiJobQueueService(repository, new ObjectMapper());

    @Test
    void enqueueUsesStableSessionOperationKey() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(service, "maxAttempts", 4);

        service.enqueueNote(sessionId, userId, true);

        verify(repository).enqueueNote(any(UUID.class), eq(userId),
                eq("NOTE_GENERATION:" + sessionId), contains(sessionId.toString()), eq(true), eq(4));
    }

    @Test
    void claimsPendingJobAndRecordsWorkerLease() {
        AiJob job = job("PENDING", 0, false, false);
        when(repository.findNextForUpdate()).thenReturn(Optional.of(job));

        AiJob claimed = service.claimNext("worker-a").orElseThrow();

        assertEquals("RUNNING", claimed.getStatus());
        assertEquals(1, claimed.getAttempts());
        assertEquals("worker-a", claimed.getLockedBy());
        assertNotNull(claimed.getLockedAt());
        verify(repository).save(job);
    }

    @Test
    void successfulJobWithConcurrentUpdateReturnsToPending() {
        AiJob job = job("RUNNING", 1, true, true);
        when(repository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertTrue(service.complete(job.getId()));

        assertEquals("PENDING", job.getStatus());
        assertEquals(0, job.getAttempts());
        assertTrue(job.getFinalizeRequested());
        assertFalse(job.getRerunRequested());
        assertNull(job.getLockedBy());
    }

    @Test
    void successfulJobWithoutNewUpdateIsTerminal() {
        AiJob job = job("RUNNING", 1, true, false);
        when(repository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        assertFalse(service.complete(job.getId()));

        assertEquals("SUCCEEDED", job.getStatus());
        assertFalse(job.getFinalizeRequested());
    }

    @Test
    void failedJobRetriesUntilMaximumAttempts() {
        AiJob retrying = job("RUNNING", 1, false, false);
        when(repository.findByIdForUpdate(retrying.getId())).thenReturn(Optional.of(retrying));
        assertTrue(service.fail(retrying.getId(), new IllegalStateException("temporary")));
        assertEquals("PENDING", retrying.getStatus());
        assertEquals("temporary", retrying.getLastError());

        AiJob exhausted = job("RUNNING", 3, false, false);
        when(repository.findByIdForUpdate(exhausted.getId())).thenReturn(Optional.of(exhausted));
        assertFalse(service.fail(exhausted.getId(), new IllegalStateException("still broken")));
        assertEquals("FAILED", exhausted.getStatus());
    }

    @Test
    void permanentFailureNeverReturnsToPending() {
        AiJob job = job("RUNNING", 1, false, false);
        job.setLockedBy("worker-a");
        job.setLockedAt(OffsetDateTime.now());
        when(repository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));

        service.failPermanently(job.getId(), new IllegalStateException("missing credential"));

        assertEquals("FAILED", job.getStatus());
        assertEquals("missing credential", job.getLastError());
        assertNull(job.getLockedBy());
        assertNull(job.getLockedAt());
        verify(repository).save(job);
    }

    private AiJob job(String status, int attempts, boolean finalize, boolean rerun) {
        return AiJob.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()).jobType("NOTE_GENERATION")
                .operationKey("NOTE_GENERATION:" + UUID.randomUUID())
                .payloadJson(new ObjectMapper().createObjectNode().put("sessionId", UUID.randomUUID().toString()))
                .status(status).attempts(attempts).maxAttempts(3)
                .finalizeRequested(finalize).rerunRequested(rerun)
                .availableAt(OffsetDateTime.now()).createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now()).build();
    }
}
