package com.omnistudy.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

@Component
@RequiredArgsConstructor
public class ReviewPlanTool implements AgentTool {
    private final KnowledgeService knowledgeService;
    public AgentToolSpec spec() { return new AgentToolSpec(AgentToolName.GET_REVIEW_PLAN,
            "读取到期和薄弱知识点，为用户制定聚焦复习计划", "{\"type\":\"object\",\"properties\":{}}", true, false, 2000); }
    public AgentToolResult execute(AgentExecutionContext context, JsonNode arguments) {
        var points = new LinkedHashMap<java.util.UUID, com.omnistudy.model.entity.KnowledgePoint>();
        knowledgeService.duePoints(context.userId()).forEach(point -> points.put(point.getId(), point));
        knowledgeService.weakPoints(context.userId()).forEach(point -> points.putIfAbsent(point.getId(), point));
        var selected = points.values().stream().limit(5).toList();
        if (selected.isEmpty()) return AgentToolResult.of("当前没有到期或薄弱知识点。");
        StringBuilder observation = new StringBuilder("待复习知识点：\n");
        var sources = selected.stream().map(point -> {
            double mastery = point.getMasteryScore() == null ? 0D : point.getMasteryScore();
            String detail = "掌握度 %.0f%%，下次复习 %s".formatted(mastery * 100,
                    point.getNextReviewAt() == null ? "未安排" : point.getNextReviewAt().toLocalDate());
            observation.append("- ").append(point.getName()).append("：").append(detail).append("\n");
            return new AgentChatResponse.Source("知识点 · " + point.getName(), detail, null, null);
        }).toList();
        return new AgentToolResult(observation.toString(), sources, null, null);
    }
}
