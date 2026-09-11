package com.omnistudy.service;

import com.omnistudy.model.entity.StudySession;
import com.omnistudy.model.dto.SessionResponse;
import com.omnistudy.repository.QuestionRepository;
import com.omnistudy.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;

    @Transactional
    public SessionResponse start(UUID userId, String videoUrl, String videoTitle) {
        StudySession session = sessionRepository.save(StudySession.builder()
                .userId(userId)
                .videoUrl(videoUrl)
                .videoTitle(videoTitle)
                .startedAt(OffsetDateTime.now())
                .build());
        return toResponse(session);
    }

    @Transactional
    public SessionResponse end(UUID sessionId, UUID userId) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问");
        }
        session.setEndedAt(OffsetDateTime.now());
        return toResponse(sessionRepository.save(session));
    }

    public List<SessionResponse> listByUser(UUID userId) {
        return sessionRepository.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public StudySession get(UUID sessionId, UUID userId) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问");
        }
        return session;
    }

    private SessionResponse toResponse(StudySession session) {
        String videoUrl = session.getVideoUrl();
        String platform = videoUrl != null && videoUrl.contains("bilibili.com")
                ? "bilibili"
                : "unknown";
        return new SessionResponse(
                session.getId().toString(),
                session.getUserId().toString(),
                session.getCourseId() == null ? null : session.getCourseId().toString(),
                platform,
                videoUrl,
                session.getVideoTitle(),
                session.getStartedAt() == null ? null : session.getStartedAt().toString(),
                session.getEndedAt() == null ? null : session.getEndedAt().toString(),
                questionRepository.countBySessionIdAndOrigin(session.getId(), "course_intercept")
        );
    }
}
