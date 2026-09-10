package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.exception.MissingAiCredentialException;
import com.omnistudy.model.entity.AiJob;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiJobWorkerTest {
    private final AiJobQueueService queue = mock(AiJobQueueService.class);
    private final NoteService notes = mock(NoteService.class);
    private final AiJobMetrics metrics = mock(AiJobMetrics.class);
    private final AiJobWorker worker = new AiJobWorker(queue, notes, metrics);

    @Test
    void terminalFailureMarksNoteAsFailed() {
        UUID sessionId = UUID.randomUUID();
        AiJob job = AiJob.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .jobType("NOTE_GENERATION")
                .payloadJson(new ObjectMapper().createObjectNode().put("sessionId", sessionId.toString()))
                .finalizeRequested(false).attempts(3).maxAttempts(3)
                .status("RUNNING").availableAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        when(queue.claimNext(any())).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("model unavailable")).when(notes)
                .generatePrepared(any(), eq(job.getUserId()), eq(false));
        when(queue.fail(eq(job.getId()), any())).thenReturn(false);
        ReflectionTestUtils.setField(worker, "claimBatchSize", 1);

        worker.poll();

        verify(notes).markGenerationFailed(sessionId);
        verify(metrics).record(eq("failed"), any());
    }

    @Test
    void concurrentUpdateKeepsNoteGeneratingForRerun() {
        UUID sessionId = UUID.randomUUID();
        AiJob job = AiJob.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .jobType("NOTE_GENERATION")
                .payloadJson(new ObjectMapper().createObjectNode().put("sessionId", sessionId.toString()))
                .finalizeRequested(true).attempts(1).maxAttempts(3)
                .status("RUNNING").availableAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        when(queue.claimNext(any())).thenReturn(Optional.of(job));
        when(queue.complete(job.getId())).thenReturn(true);
        ReflectionTestUtils.setField(worker, "claimBatchSize", 1);

        worker.poll();

        verify(notes).markGenerationPending(sessionId);
        verify(metrics).record(eq("rerun"), any());
    }

    @Test
    void missingUserKeyFailsWithoutRetry() {
        UUID sessionId = UUID.randomUUID();
        AiJob job = AiJob.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .jobType("NOTE_GENERATION")
                .payloadJson(new ObjectMapper().createObjectNode().put("sessionId", sessionId.toString()))
                .finalizeRequested(false).attempts(1).maxAttempts(3)
                .status("RUNNING").availableAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now()).updatedAt(OffsetDateTime.now()).build();
        when(queue.claimNext(any())).thenReturn(Optional.of(job));
        doThrow(new MissingAiCredentialException("请配置 Key")).when(notes)
                .generatePrepared(any(), eq(job.getUserId()), eq(false));
        ReflectionTestUtils.setField(worker, "claimBatchSize", 1);

        worker.poll();

        verify(queue).failPermanently(eq(job.getId()), any(MissingAiCredentialException.class));
        verify(queue, never()).fail(any(), any());
        verify(notes).markGenerationFailed(sessionId);
        verify(metrics).record(eq("failed"), any());
    }
}
