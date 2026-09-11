package com.omnistudy.service;

import com.omnistudy.model.entity.StudySession;
import com.omnistudy.repository.QuestionRepository;
import com.omnistudy.repository.SessionRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    @Test
    void countsOnlyCourseInterceptQuestions() {
        SessionRepository sessions = mock(SessionRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        SessionService service = new SessionService(sessions, questions);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        StudySession session = StudySession.builder()
                .id(sessionId)
                .userId(userId)
                .videoUrl("https://www.bilibili.com/video/BV1test")
                .videoTitle("测试课程")
                .startedAt(OffsetDateTime.now())
                .build();

        when(sessions.findByUserIdOrderByStartedAtDesc(userId)).thenReturn(List.of(session));
        when(questions.countBySessionIdAndOrigin(sessionId, "course_intercept")).thenReturn(3);

        var response = service.listByUser(userId).get(0);

        assertThat(response.questionCount()).isEqualTo(3);
        verify(questions).countBySessionIdAndOrigin(sessionId, "course_intercept");
    }
}
