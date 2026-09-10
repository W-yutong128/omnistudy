package com.omnistudy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiTask;
import com.omnistudy.model.dto.EvaluateRequest;
import com.omnistudy.model.dto.EvaluateResponse;
import com.omnistudy.model.entity.Attempt;
import com.omnistudy.model.entity.Question;
import com.omnistudy.model.prompt.PromptTemplates;
import com.omnistudy.repository.AttemptRepository;
import com.omnistudy.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluateService {

    private final MeteredAiService meteredAiService;
    private final QuestionRepository questionRepository;
    private final AttemptRepository attemptRepository;
    private final ObjectMapper objectMapper;
    private final KnowledgeService knowledgeService;
    private final NoteService noteService;

    private static final Pattern QUESTION_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([0-9a-fA-F-]{36})\\\"");

    @Transactional
    public EvaluateResponse evaluate(EvaluateRequest request, UUID userId) {
        UUID questionId;
        try {
            Matcher matcher = QUESTION_ID.matcher(request.questionJson());
            if (!matcher.find()) throw new IllegalArgumentException();
            questionId = UUID.fromString(matcher.group(1));
        } catch (Exception e) {
            throw new IllegalArgumentException("questionJson 中需要包含题目 id");
        }

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (!question.getUserId().equals(userId)) throw new IllegalArgumentException("无权访问该问题");

        String userPrompt = PromptTemplates.buildEvaluateUserPrompt(
                request.questionJson(),
                request.answer(),
                request.historyJson() == null ? "[]" : request.historyJson(),
                request.attempt()
        );

        String raw = meteredAiService.generate(userId, AiTask.EVALUATE_ANSWER, AiRequest.text(
                PromptTemplates.SYSTEM_PROMPT.replace("苏格拉底式互动伴学导师", "苏格拉底式评估导师"),
                userPrompt
        ));

        log.debug("Evaluate raw response: {}", raw);

        EvaluateResponse response = parse(raw, request.attempt());

        Attempt attempt = Attempt.builder()
                .questionId(question.getId())
                .attemptNum(request.attempt())
                .answerText(request.answer())
                .evaluationJson(parseNode(raw))
                .decided(response.decision())
                .createdAt(OffsetDateTime.now())
                .build();
        attemptRepository.save(attempt);

        if (question.getKnowledgePointId() != null) {
            knowledgeService.recordReview(question.getKnowledgePointId(), "pass".equals(response.decision()));
            noteService.refreshRecordedWeakPoints(question.getSessionId(), userId);
        }

        return response;
    }

    private EvaluateResponse parse(String raw, int attempt) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            return new EvaluateResponse(
                    node.path("decision").asText("support"),
                    node.path("feedback").asText(""),
                    node.hasNonNull("nextQuestion") ? node.path("nextQuestion").asText() : null,
                    node.hasNonNull("hint") ? node.path("hint").asText() : null,
                    attempt
            );
        } catch (Exception e) {
            log.error("Parse evaluate failed", e);
            return new EvaluateResponse("support", "解析失败，请重试", null, "请再想想", attempt);
        }
    }

    private JsonNode parseNode(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return objectMapper.createObjectNode().put("raw", raw);
        }
    }
}
