package com.omnistudy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiTask;
import com.omnistudy.model.dto.InterceptRequest;
import com.omnistudy.model.dto.InterceptResponse;
import com.omnistudy.model.dto.ChoiceAnswerResponse;
import com.omnistudy.model.entity.Attempt;
import com.omnistudy.model.entity.Question;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.model.prompt.PromptTemplates;
import com.omnistudy.repository.QuestionRepository;
import com.omnistudy.repository.NoteRepository;
import com.omnistudy.repository.AttemptRepository;
import com.omnistudy.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterceptService {

    private final MeteredAiService meteredAiService;
    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;
    private final NoteRepository noteRepository;
    private final NoteService noteService;

    @Transactional
    public InterceptResultWithId intercept(InterceptRequest request, UUID userId) {
        StudySession session = sessionRepository.findById(UUID.fromString(request.sessionId()))
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + request.sessionId()));

        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该 session");
        }

        String userPrompt = PromptTemplates.buildInterceptUserPrompt(
                request.time(),
                request.before(),
                request.current(),
                request.after(),
                request.difficulty()
        );

        String raw = meteredAiService.generate(userId, AiTask.INTERCEPT, AiRequest.vision(
                PromptTemplates.SYSTEM_PROMPT, userPrompt, request.screenshot()));

        log.debug("LLM raw response: {}", raw);

        InterceptResponse response = parse(raw, request.time(), request.part());

        UUID questionId = null;
        if (response.shouldIntercept()) {
            UUID sourceNoteId = noteRepository.findBySessionId(session.getId()).map(note -> note.getId()).orElse(null);
            var point = response.coreConcept() == null || response.coreConcept().isBlank() ? null
                    : knowledgeService.upsert(userId, response.coreConcept(), sourceNoteId);
            Question q = Question.builder()
                    .sessionId(session.getId())
                    .userId(userId)
                    .noteId(sourceNoteId)
                    .knowledgePointId(point == null ? null : point.getId())
                    .t(parseTime(request.time()))
                    .promptJson(parseToNode(raw))
                    .difficulty(response.difficulty())
                    .part(request.part() != null ? request.part() : 1)
                    .createdAt(OffsetDateTime.now())
                    .build();
            questionId = questionRepository.save(q).getId();
        }

        return new InterceptResultWithId(questionId, response);
    }

    private InterceptResponse parse(String raw, String time, Integer part) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.path("shouldIntercept").asBoolean()) {
                return InterceptResponse.noIntercept(
                        node.path("reason").asText("信息不足"),
                        time
                );
            }
            List<InterceptResponse.Option> options = parseOptions(node.path("options"));
            String correctOptionId = node.path("correctOptionId").asText();
            String explanation = node.path("explanation").asText();
            boolean validChoice = options.size() == 2
                    && options.stream().anyMatch(option -> option.id().equals(correctOptionId))
                    && !explanation.isBlank();
            if (!validChoice) return InterceptResponse.noIntercept("模型没有生成有效的二选一题", time);
            return new InterceptResponse(
                    true,
                    node.path("reason").asText(null),
                    time, // 强制使用请求中的 time，不依赖 LLM 返回值
                    node.path("coreConcept").asText(),
                    node.path("evidence").asText(),
                    node.path("question").asText(),
                    node.path("questionType").asText("failure_scenario"),
                    node.path("difficulty").asInt(2),
                    node.path("confidence").asDouble(0.8),
                    part,
                    options,
                    correctOptionId,
                    explanation
            );
        } catch (JsonProcessingException e) {
            log.error("Parse intercept result failed", e);
            return InterceptResponse.noIntercept("解析失败", time);
        }
    }

    private List<InterceptResponse.Option> parseOptions(JsonNode node) {
        if (!node.isArray() || node.size() != 2) return List.of();
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(v -> new InterceptResponse.Option(v.path("id").asText(), v.path("text").asText()))
                .filter(v -> !v.id().isBlank() && !v.text().isBlank()).toList();
    }

    @Transactional
    public ChoiceAnswerResponse answerChoice(UUID questionId, String optionId, UUID userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (!question.getUserId().equals(userId)) throw new IllegalArgumentException("无权访问该问题");

        JsonNode prompt = question.getPromptJson();
        String correctOptionId = prompt.path("correctOptionId").asText();
        String explanation = prompt.path("explanation").asText("请回顾刚才的视频内容。");
        if (correctOptionId.isBlank()) throw new IllegalArgumentException("该题不是选择题");
        boolean correct = correctOptionId.equals(optionId);
        var evaluation = objectMapper.createObjectNode()
                .put("selectedOptionId", optionId).put("correctOptionId", correctOptionId)
                .put("correct", correct).put("feedback", explanation);
        attemptRepository.save(Attempt.builder()
                .questionId(questionId).attemptNum(attemptRepository.countByQuestionId(questionId) + 1)
                .answerText(optionId == null ? "" : optionId).evaluationJson(evaluation)
                .decided(correct ? "pass" : "support").createdAt(OffsetDateTime.now()).build());
        var point = question.getKnowledgePointId() == null ? null
                : knowledgeService.recordReview(question.getKnowledgePointId(), correct);
        if (question.getNoteId() != null && point != null) {
            noteRepository.findById(question.getNoteId()).ifPresent(note -> {
                note.setMasteryStatus(point.getMasteryScore() >= 0.75 ? "mastered"
                        : correct ? "practicing" : "review");
                note.setNextReviewAt(point.getNextReviewAt());
                note.setUpdatedAt(OffsetDateTime.now());
                noteRepository.save(note);
            });
        }
        if (point != null) noteService.refreshRecordedWeakPoints(question.getSessionId(), userId);
        return new ChoiceAnswerResponse(correct, correctOptionId, explanation,
                point == null ? null : point.getMasteryScore(),
                point == null ? null : point.getMastery(),
                point == null || point.getNextReviewAt() == null ? null : point.getNextReviewAt().toString());
    }

    private JsonNode parseToNode(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode().put("raw", raw);
        }
    }

    private Float parseTime(String time) {
        try {
            String[] parts = time.trim().split(":");
            if (parts.length == 1) {
                return Float.parseFloat(parts[0]);
            }
            if (parts.length == 2) {
                return Float.parseFloat(parts[0]) * 60 + Float.parseFloat(parts[1]);
            }
            if (parts.length == 3) {
                return Float.parseFloat(parts[0]) * 3600
                        + Float.parseFloat(parts[1]) * 60
                        + Float.parseFloat(parts[2]);
            }
            throw new NumberFormatException("Unsupported time format: " + time);
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("Invalid intercept time '{}', falling back to 0", time);
            return 0f;
        }
    }

    @Transactional(readOnly = true)
    public List<StoredQuestion> listBySession(UUID sessionId, UUID userId) {
        StudySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该 session");
        }

        return questionRepository.findBySessionIdAndOriginOrderByCreatedAtAsc(sessionId, "course_intercept").stream()
                .map(q -> new StoredQuestion(
                        q.getId(),
                        formatTime(q.getT()),
                        q.getPromptJson().path("coreConcept").asText(""),
                        q.getPromptJson().path("evidence").asText(""),
                        q.getPromptJson().path("question").asText(""),
                        q.getPromptJson().path("questionType").asText("failure_scenario"),
                        q.getDifficulty(),
                        q.getPromptJson().path("confidence").asDouble(0.8),
                        q.getCreatedAt(),
                        q.getPart(),
                        parseOptions(q.getPromptJson().path("options"))
                ))
                .toList();
    }

    @Transactional
    public void deleteById(UUID questionId, UUID userId) {
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        if (!question.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除该问题");
        }

        questionRepository.deleteById(questionId);
    }

    private String formatTime(Float seconds) {
        int totalSeconds = Math.max(0, Math.round(seconds == null ? 0 : seconds));
        int minutes = totalSeconds / 60;
        int remainingSeconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, remainingSeconds);
    }

    public record InterceptResultWithId(UUID questionId, InterceptResponse response) {}

    public record StoredQuestion(
            UUID id,
            String time,
            String coreConcept,
            String evidence,
            String question,
            String questionType,
            Integer difficulty,
            double confidence,
            OffsetDateTime createdAt,
            Integer part
            , List<InterceptResponse.Option> options
    ) {}
}
