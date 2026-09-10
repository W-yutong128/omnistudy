package com.omnistudy.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record QuestionBankResponse(
        String id, String sourceDocument, Integer sourceYear, String sourceExam,
        Integer sourceQuestionNo, Integer sourcePage, String subject, String questionType,
        String questionText, JsonNode options, JsonNode knowledgeTags,
        JsonNode assetPaths, Integer difficulty, String licenseStatus, String reviewStatus
) {}
