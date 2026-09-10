package com.omnistudy.service;

import com.omnistudy.exception.MissingAiCredentialException;
import com.omnistudy.model.dto.NoteGenerateRequest;
import com.omnistudy.model.entity.AiJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai-jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiJobWorker {
    private final AiJobQueueService queueService;
    private final NoteService noteService;
    private final AiJobMetrics metrics;
    private final String workerId = UUID.randomUUID().toString();

    @Value("${app.ai-jobs.claim-batch-size:2}")
    private int claimBatchSize;
    @Value("${app.ai-jobs.stale-after-seconds:300}")
    private long staleAfterSeconds;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        recoverStale();
    }

    @Scheduled(fixedDelayString = "${app.ai-jobs.poll-delay-ms:1000}")
    public void poll() {
        for (int index = 0; index < Math.max(1, claimBatchSize); index += 1) {
            var claimed = queueService.claimNext(workerId);
            if (claimed.isEmpty()) return;
            execute(claimed.get());
        }
    }

    @Scheduled(fixedDelayString = "${app.ai-jobs.recovery-delay-ms:60000}")
    public void recoverStale() {
        int recovered = queueService.recoverStale(OffsetDateTime.now().minusSeconds(staleAfterSeconds));
        metrics.recovered(recovered);
        if (recovered > 0) log.warn("Recovered {} stale AI jobs", recovered);
    }

    private void execute(AiJob job) {
        Instant started = Instant.now();
        UUID sessionId = null;
        try {
            if (!"NOTE_GENERATION".equals(job.getJobType())) {
                throw new IllegalArgumentException("未知 AI job 类型: " + job.getJobType());
            }
            sessionId = UUID.fromString(job.getPayloadJson().path("sessionId").asText());
            noteService.generatePrepared(new NoteGenerateRequest(sessionId.toString()), job.getUserId(),
                    Boolean.TRUE.equals(job.getFinalizeRequested()));
            boolean rerun = queueService.complete(job.getId());
            if (rerun) noteService.markGenerationPending(sessionId);
            metrics.record(rerun ? "rerun" : "succeeded", Duration.between(started, Instant.now()));
            log.info("AI job completed id={} type={} rerun={}", job.getId(), job.getJobType(), rerun);
        } catch (MissingAiCredentialException error) {
            queueService.failPermanently(job.getId(), error);
            metrics.record("failed", Duration.between(started, Instant.now()));
            log.warn("AI job stopped because the user has no API key id={} type={}", job.getId(), job.getJobType());
            if (sessionId != null) noteService.markGenerationFailed(sessionId);
        } catch (Exception error) {
            boolean retry = queueService.fail(job.getId(), error);
            metrics.record(retry ? "retry" : "failed", Duration.between(started, Instant.now()));
            log.error("AI job failed id={} type={} retry={}", job.getId(), job.getJobType(), retry, error);
            if (!retry && sessionId != null) noteService.markGenerationFailed(sessionId);
        }
    }
}
