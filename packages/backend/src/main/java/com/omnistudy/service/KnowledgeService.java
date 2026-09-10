package com.omnistudy.service;

import com.omnistudy.model.entity.KnowledgePoint;
import com.omnistudy.model.dto.KnowledgePointResponse;
import com.omnistudy.repository.KnowledgePointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KnowledgePointRepository repository;

    public List<KnowledgePoint> listByUser(UUID userId) {
        return repository.findByUserIdOrderByLastReviewedDesc(userId);
    }

    public List<KnowledgePointResponse> listResponses(UUID userId) {
        return listByUser(userId).stream().map(this::toResponse).toList();
    }

    public List<KnowledgePoint> weakPoints(UUID userId) {
        return repository.findWeakPointsByUserId(userId);
    }

    public List<KnowledgePointResponse> weakPointResponses(UUID userId) {
        return weakPoints(userId).stream().map(this::toResponse).toList();
    }

    public List<KnowledgePoint> weakPointsForSession(UUID userId, UUID sessionId) {
        return repository.findWeakPointsByUserIdAndSessionId(userId, sessionId);
    }

    public List<KnowledgePoint> duePoints(UUID userId) {
        return repository.findByUserIdAndNextReviewAtLessThanEqualOrderByNextReviewAtAsc(userId, OffsetDateTime.now());
    }

    public List<KnowledgePointResponse> duePointResponses(UUID userId) {
        return duePoints(userId).stream().map(this::toResponse).toList();
    }

    /**
     * 简单的归一化匹配：把学生错误答案中提到的概念归并到知识点
     */
    public KnowledgePoint upsert(UUID userId, String name) {
        return upsert(userId, name, null);
    }

    public KnowledgePoint upsert(UUID userId, String name, UUID sourceNoteId) {
        String normalized = normalize(name);
        if (normalized.isBlank()) throw new IllegalArgumentException("知识点名称不能为空");
        return repository.findByUserIdAndNormalizedName(userId, normalized)
                .map(existing -> {
                    attachSource(existing, sourceNoteId);
                    return repository.save(existing);
                })
                .orElseGet(() -> repository.save(KnowledgePoint.builder()
                        .userId(userId)
                        .name(name.trim())
                        .normalizedName(normalized)
                        .mastery("陌生的")
                        .sources(sourceNoteId == null ? new ArrayList<>() : new ArrayList<>(List.of(sourceNoteId)))
                        .build()));
    }

    /**
     * 一个轻量的间隔复习调度器。答对后逐步拉长间隔；答错后降低掌握度并在次日重试。
     */
    public KnowledgePoint recordReview(UUID knowledgePointId, boolean correct) {
        KnowledgePoint point = repository.findById(knowledgePointId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge point not found"));
        double score = point.getMasteryScore() == null ? 0.20 : point.getMasteryScore();
        int streak = point.getCorrectStreak() == null ? 0 : point.getCorrectStreak();
        int interval = point.getReviewIntervalDays() == null ? 1 : point.getReviewIntervalDays();

        if (correct) {
            streak += 1;
            score = Math.min(1.0, score + 0.16 + Math.min(streak, 4) * 0.02);
            interval = streak == 1 ? 2 : Math.min(60, Math.max(3, (int) Math.round(interval * 2.2)));
        } else {
            streak = 0;
            score = Math.max(0.0, score - 0.22);
            interval = 1;
        }

        OffsetDateTime now = OffsetDateTime.now();
        point.setCorrectStreak(streak);
        point.setMasteryScore(score);
        point.setReviewIntervalDays(interval);
        point.setLastReviewed(now);
        point.setNextReviewAt(now.plusDays(interval));
        point.setMastery(score >= 0.75 ? "掌握的" : score >= 0.40 ? "模糊的" : "陌生的");
        return repository.save(point);
    }

    private void attachSource(KnowledgePoint point, UUID sourceNoteId) {
        if (sourceNoteId == null) return;
        List<UUID> sources = point.getSources() == null ? new ArrayList<>() : new ArrayList<>(point.getSources());
        if (!sources.contains(sourceNoteId)) sources.add(sourceNoteId);
        point.setSources(sources);
    }

    private String normalize(String name) {
        return name == null ? "" : name.toLowerCase().replaceAll("[\\s，。；：、,.!?！？:;]+", "").trim();
    }

    private KnowledgePointResponse toResponse(KnowledgePoint point) {
        List<String> sources = point.getSources() == null ? List.of()
                : point.getSources().stream().map(UUID::toString).toList();
        return new KnowledgePointResponse(
                point.getId().toString(), point.getName(), point.getNormalizedName(), point.getMastery(),
                text(point.getFirstSeen()), text(point.getLastReviewed()),
                point.getMasteryScore() == null ? 0.0 : point.getMasteryScore(),
                text(point.getNextReviewAt()),
                point.getReviewIntervalDays() == null ? 1 : point.getReviewIntervalDays(),
                point.getCorrectStreak() == null ? 0 : point.getCorrectStreak(),
                sources, sources.size());
    }

    private String text(OffsetDateTime value) {
        return value == null ? null : value.toString();
    }
}
