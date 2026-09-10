package com.omnistudy.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InterceptResponse(
    boolean shouldIntercept,
    String reason,
    String time,
    String coreConcept,
    String evidence,
    String question,
    String questionType,
    int difficulty,
    double confidence,
    Integer part,
    List<Option> options,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String correctOptionId,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String explanation
) {
    public record Option(String id, String text) {}

    public static InterceptResponse noIntercept(String reason, String time) {
        return new InterceptResponse(false, reason, time, "", "", "", "", 1, 0.0, null,
                List.of(), "", "");
    }
}
