package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.model.entity.Question;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.repository.AttemptRepository;
import com.omnistudy.repository.NoteRepository;
import com.omnistudy.repository.QuestionRepository;
import com.omnistudy.repository.SessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterceptServiceTest {

    @Test
    void listsOnlyCourseInterceptQuestionsForLearningPanel() {
        SessionRepository sessions = mock(SessionRepository.class);
        QuestionRepository questions = mock(QuestionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        InterceptService service = new InterceptService(
                mock(MeteredAiService.class), sessions, questions, mock(AttemptRepository.class),
                objectMapper, mock(KnowledgeService.class), mock(NoteRepository.class), mock(NoteService.class));
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        var prompt = objectMapper.createObjectNode();
        prompt.put("question", "视频中的课程问题");
        prompt.put("questionType", "recall");
        Question courseQuestion = Question.builder()
                .id(UUID.randomUUID()).sessionId(sessionId).userId(userId)
                .origin("course_intercept").t(12f).part(1).promptJson(prompt).build();

        when(sessions.findById(sessionId)).thenReturn(Optional.of(
                StudySession.builder().id(sessionId).userId(userId).build()));
        when(questions.findBySessionIdAndOriginOrderByCreatedAtAsc(sessionId, "course_intercept"))
                .thenReturn(List.of(courseQuestion));

        var result = service.listBySession(sessionId, userId);

        assertThat(result).extracting(InterceptService.StoredQuestion::question)
                .containsExactly("视频中的课程问题");
        verify(questions).findBySessionIdAndOriginOrderByCreatedAtAsc(sessionId, "course_intercept");
    }
}
