package com.omnistudy.service;

import com.omnistudy.model.entity.KnowledgePoint;
import com.omnistudy.repository.KnowledgePointRepository;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeServiceTest {

    @Test
    void correctAnswerRaisesMasteryAndExpandsInterval() {
        UUID id = UUID.randomUUID();
        KnowledgePoint point = KnowledgePoint.builder().id(id).name("TCP").normalizedName("tcp")
                .masteryScore(0.20).reviewIntervalDays(1).correctStreak(0).build();
        KnowledgeService service = serviceFor(point);

        KnowledgePoint reviewed = service.recordReview(id, true);

        assertThat(reviewed.getMasteryScore()).isGreaterThan(0.20);
        assertThat(reviewed.getCorrectStreak()).isEqualTo(1);
        assertThat(reviewed.getReviewIntervalDays()).isEqualTo(2);
        assertThat(reviewed.getNextReviewAt()).isAfter(OffsetDateTime.now().plusHours(47));
    }

    @Test
    void wrongAnswerResetsStreakAndSchedulesTomorrow() {
        UUID id = UUID.randomUUID();
        KnowledgePoint point = KnowledgePoint.builder().id(id).name("TCP").normalizedName("tcp")
                .masteryScore(0.80).reviewIntervalDays(14).correctStreak(3).build();
        KnowledgeService service = serviceFor(point);

        KnowledgePoint reviewed = service.recordReview(id, false);

        assertThat(reviewed.getMasteryScore()).isCloseTo(0.58, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(reviewed.getCorrectStreak()).isZero();
        assertThat(reviewed.getReviewIntervalDays()).isEqualTo(1);
        assertThat(reviewed.getMastery()).isEqualTo("模糊的");
    }

    @Test
    void sessionWeakPointsComeFromAttemptBackedRepositoryQuery() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        KnowledgePoint point = KnowledgePoint.builder().name("分页存储").normalizedName("分页存储").build();
        KnowledgePointRepository repository = org.mockito.Mockito.mock(KnowledgePointRepository.class);
        org.mockito.Mockito.when(repository.findWeakPointsByUserIdAndSessionId(userId, sessionId))
                .thenReturn(List.of(point));
        KnowledgeService service = new KnowledgeService(repository);

        assertThat(service.weakPointsForSession(userId, sessionId)).containsExactly(point);
        org.mockito.Mockito.verify(repository).findWeakPointsByUserIdAndSessionId(userId, sessionId);
    }

    private KnowledgeService serviceFor(KnowledgePoint point) {
        KnowledgePointRepository repository = (KnowledgePointRepository) Proxy.newProxyInstance(
                KnowledgePointRepository.class.getClassLoader(),
                new Class<?>[]{KnowledgePointRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.of(point);
                    case "save" -> args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new KnowledgeService(repository);
    }
}
