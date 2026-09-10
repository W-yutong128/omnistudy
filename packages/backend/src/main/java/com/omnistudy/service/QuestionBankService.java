package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.model.dto.*;
import com.omnistudy.model.entity.KnowledgePoint;
import com.omnistudy.model.entity.Question;
import com.omnistudy.model.entity.QuestionBankItem;
import com.omnistudy.repository.KnowledgePointRepository;
import com.omnistudy.repository.QuestionBankRepository;
import com.omnistudy.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuestionBankService {
    private final QuestionBankRepository bankRepository;
    private final QuestionRepository questionRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<QuestionBankResponse> search(String query, String subject, UUID userId, int limit) {
        String normalizedQuery = blankToEmpty(query);
        String normalizedSubject = blankToEmpty(subject);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return bankRepository.search(normalizedQuery, normalizedSubject, userId, PageRequest.of(0, safeLimit))
                .stream().map(this::response).toList();
    }

    @Transactional
    public DeliveredQuestionResponse deliver(UUID bankId, QuestionDeliveryRequest request, UUID userId) {
        QuestionBankItem bank = bankRepository.findById(bankId)
                .orElseThrow(() -> new IllegalArgumentException("题库题目不存在"));
        if (!"ready".equals(bank.getReviewStatus())) throw new IllegalArgumentException("该题仍需人工审核或补充配图");

        KnowledgePoint point = resolveKnowledgePoint(bank, request, userId);
        var prompt = objectMapper.createObjectNode();
        prompt.put("coreConcept", point.getName());
        prompt.put("question", bank.getQuestionText());
        prompt.put("questionType", "practice");
        prompt.put("correctOptionId", bank.getCorrectOptionId());
        prompt.put("explanation", bank.getExplanation() == null ? "请复习对应知识点。" : bank.getExplanation());
        prompt.put("sourceLabel", sourceLabel(bank));
        prompt.set("options", bank.getOptionsJson().deepCopy());

        Question saved = questionRepository.save(Question.builder()
                .userId(userId).knowledgePointId(point.getId()).bankQuestionId(bank.getId())
                .origin("question_bank").t(0f).part(1).difficulty(bank.getDifficulty())
                .promptJson(prompt).build());
        return new DeliveredQuestionResponse(saved.getId().toString(), bank.getId().toString(),
                point.getId().toString(), bank.getSubject(), bank.getQuestionText(),
                bank.getOptionsJson(), bank.getDifficulty(), sourceLabel(bank));
    }

    private KnowledgePoint resolveKnowledgePoint(QuestionBankItem bank, QuestionDeliveryRequest request, UUID userId) {
        if (request != null && request.knowledgePointId() != null && !request.knowledgePointId().isBlank()) {
            KnowledgePoint point = knowledgePointRepository.findById(UUID.fromString(request.knowledgePointId()))
                    .orElseThrow(() -> new IllegalArgumentException("知识点不存在"));
            if (!point.getUserId().equals(userId)) throw new IllegalArgumentException("无权使用该知识点");
            return point;
        }
        String tag = bank.getKnowledgeTags() != null && bank.getKnowledgeTags().isArray() && !bank.getKnowledgeTags().isEmpty()
                ? bank.getKnowledgeTags().get(0).asText(bank.getSubject()) : bank.getSubject();
        return knowledgeService.upsert(userId, tag);
    }

    private QuestionBankResponse response(QuestionBankItem q) {
        return new QuestionBankResponse(q.getId().toString(), q.getSourceDocument(), q.getSourceYear(),
                q.getSourceExam(), q.getSourceQuestionNo(), q.getSourcePage(), q.getSubject(), q.getQuestionType(),
                q.getQuestionText(), q.getOptionsJson(), q.getKnowledgeTags(), q.getAssetPaths(),
                q.getDifficulty(), q.getLicenseStatus(), q.getReviewStatus());
    }

    private String sourceLabel(QuestionBankItem q) {
        return (q.getSourceYear() == null ? "" : q.getSourceYear() + "年") + q.getSourceExam() + " 第" + q.getSourceQuestionNo() + "题";
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }
}
