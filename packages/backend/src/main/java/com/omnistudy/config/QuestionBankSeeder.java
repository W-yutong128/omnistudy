package com.omnistudy.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.model.entity.QuestionBankItem;
import com.omnistudy.repository.QuestionBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.question-bank", name = "seed-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class QuestionBankSeeder implements ApplicationRunner {
    private static final String RESOURCE = "question-bank/sample-single-choice.json";
    private final QuestionBankRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        SeedQuestion[] questions;
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            questions = objectMapper.readValue(input, SeedQuestion[].class);
        }
        int inserted = 0;
        for (SeedQuestion q : questions) {
            if (repository.findBySourceDocumentAndSourceQuestionNo(q.sourceDocument(), q.sourceQuestionNo()).isPresent()) continue;
            repository.save(QuestionBankItem.builder()
                    .sourceType(q.sourceType()).sourceDocument(q.sourceDocument()).sourceYear(q.sourceYear())
                    .sourceExam(q.sourceExam()).sourceQuestionNo(q.sourceQuestionNo()).sourcePage(q.sourcePage())
                    .subject(q.subject()).questionType(q.questionType()).questionText(q.questionText())
                    .optionsJson(q.options()).correctOptionId(q.correctOptionId()).explanation(q.explanation())
                    .knowledgeTags(q.knowledgeTags()).assetPaths(q.assetPaths()).difficulty(q.difficulty())
                    .rawText(q.rawText()).licenseStatus(q.licenseStatus()).reviewStatus(q.reviewStatus()).build());
            inserted++;
        }
        log.info("Question bank seed: {} records checked, {} inserted", questions.length, inserted);
    }

    private record SeedQuestion(
            String sourceType, String sourceDocument, Integer sourceYear, String sourceExam,
            Integer sourceQuestionNo, Integer sourcePage, String subject, String questionType,
            String questionText, JsonNode options, String correctOptionId, String explanation,
            JsonNode knowledgeTags, JsonNode assetPaths, Integer difficulty, String rawText,
            String licenseStatus, String reviewStatus
    ) {}
}
