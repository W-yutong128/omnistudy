package com.omnistudy.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record DeliveredQuestionResponse(
        String id, String bankQuestionId, String knowledgePointId, String subject,
        String question, JsonNode options, Integer difficulty, String sourceLabel
) {}

