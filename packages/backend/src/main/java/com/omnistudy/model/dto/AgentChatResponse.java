package com.omnistudy.model.dto;

import java.util.List;

public record AgentChatResponse(
        String reply,
        String tool,
        String skill,
        List<Source> sources,
        Double seekTo,
        Practice practice,
        IdeHandoff ideHandoff
) {
    public record Source(String title, String snippet, Integer part, Double startTime) {}
    public record Practice(
            String question,
            List<InterceptResponse.Option> options,
            String correctOptionId,
            String explanation
    ) {}
    public record IdeHandoff(String reason, String suggestedFile) {}
}
