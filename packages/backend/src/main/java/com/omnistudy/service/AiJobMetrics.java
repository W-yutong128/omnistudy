package com.omnistudy.service;

import com.omnistudy.repository.AiJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai-jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiJobMetrics {
    private final AiJobRepository repository;
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> outcomes = new ConcurrentHashMap<>();
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();
    private Timer duration;

    @PostConstruct
    void bind() {
        for (String status : new String[]{"PENDING", "RUNNING", "FAILED"}) {
            Gauge.builder("omnistudy.ai_jobs.queue", repository, repo -> safeCount(repo, status))
                    .description("Current durable AI job queue size")
                    .tag("status", status.toLowerCase())
                    .register(meterRegistry);
        }
        Gauge.builder("omnistudy.ai_jobs.last_success_epoch_seconds", lastSuccessEpochSeconds, AtomicLong::get)
                .description("Unix timestamp of the last successful AI job")
                .register(meterRegistry);
        duration = Timer.builder("omnistudy.ai_jobs.duration")
                .description("AI job execution duration")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void record(String outcome, Duration elapsed) {
        outcomes.computeIfAbsent(outcome, value -> Counter.builder("omnistudy.ai_jobs.outcomes")
                        .description("AI job execution outcomes")
                        .tag("outcome", value)
                        .register(meterRegistry))
                .increment();
        duration.record(elapsed);
        if ("succeeded".equals(outcome) || "rerun".equals(outcome)) {
            lastSuccessEpochSeconds.set(System.currentTimeMillis() / 1_000);
        }
    }

    public void recovered(int count) {
        if (count > 0) {
            meterRegistry.counter("omnistudy.ai_jobs.recovered").increment(count);
        }
    }

    private double safeCount(AiJobRepository repo, String status) {
        try {
            return repo.countByStatus(status);
        } catch (RuntimeException error) {
            log.warn("Unable to read AI job queue metric status={}: {}", status, error.getMessage());
            return Double.NaN;
        }
    }
}
