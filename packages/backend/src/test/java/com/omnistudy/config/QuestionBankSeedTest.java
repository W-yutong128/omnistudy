package com.omnistudy.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionBankSeedTest {
    @Test
    void seedContainsOnlyCompleteProjectOwnedSampleQuestions() throws Exception {
        JsonNode questions;
        try (var input = getClass().getClassLoader().getResourceAsStream("question-bank/sample-single-choice.json")) {
            questions = new ObjectMapper().readTree(input);
        }
        assertThat(questions.size()).isEqualTo(4);
        for (JsonNode q : questions) {
            assertThat(q.path("sourceType").asText()).isEqualTo("project_sample");
            assertThat(q.path("sourceDocument").asText()).isEqualTo("omnistudy-samples-v1");
            assertThat(q.path("options").size()).isEqualTo(4);
            assertThat(q.path("correctOptionId").asText()).isIn("A", "B", "C", "D");
            assertThat(q.path("explanation").asText()).isNotBlank();
            assertThat(q.path("licenseStatus").asText()).isEqualTo("project_original");
            assertThat(q.path("reviewStatus").asText()).isEqualTo("ready");
            assertThat(q.path("assetPaths")).isEmpty();
        }
    }
}
