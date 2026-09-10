package com.omnistudy.agentscope;

import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.service.KnowledgeService;
import com.omnistudy.service.RagService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

@Component
@RequiredArgsConstructor
public class OmniStudyAgentScopeTools {
    private final RagService ragService;
    private final KnowledgeService knowledgeService;

    @Tool(name = "explain_current", description = "读取当前课程、播放时间和字幕上下文，用于解释用户正在学习的内容", strict = true, readOnly = true)
    public String explainCurrent(AgentScopeRequestContext context) {
        var request = context.request();
        String result = """
                课程：%s
                第%d集 %.1f秒
                前文：%s
                当前：%s
                后文：%s
                """.formatted(context.session().getVideoTitle(), request.part() == null ? 1 : request.part(),
                request.currentTime() == null ? 0 : request.currentTime(), value(request.before()),
                value(request.current()), value(request.after()));
        context.record("explain_current", result);
        context.addSource(new AgentChatResponse.Source("当前视频", value(request.current()), request.part(), request.currentTime()));
        return result;
    }

    @Tool(name = "search_learning_memory", description = "搜索当前用户过去的笔记和字幕摘要；询问以前学过的内容时使用", strict = true, readOnly = true)
    public String searchLearningMemory(
            @ToolParam(name = "query", description = "要检索的知识概念") String query,
            AgentScopeRequestContext context) {
        var hits = ragService.search(context.userId(), null, query, 5);
        if (hits.isEmpty()) {
            context.record("search_learning_memory", "没有检索到匹配的学习记录。");
            return "没有检索到匹配的学习记录。";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            var hit = hits.get(i);
            result.append('[').append(i + 1).append("] ").append(hit.title()).append('\n').append(hit.content()).append('\n');
            context.addSource(new AgentChatResponse.Source(hit.title(), hit.content(), hit.part(), hit.startTime()));
        }
        context.record("search_learning_memory", result.toString());
        return result.toString();
    }

    @Tool(name = "seek_video", description = "在当前课程索引中查找概念出现的分集和视频时间", strict = true, readOnly = true)
    public String seekVideo(
            @ToolParam(name = "query", description = "需要定位的概念") String query,
            AgentScopeRequestContext context) {
        var hit = ragService.search(context.userId(), context.session().getId(), query, 5).stream()
                .filter(item -> item.startTime() != null).findFirst().orElse(null);
        if (hit == null) {
            context.record("seek_video", "当前课程索引中没有找到对应时间点。");
            return "当前课程索引中没有找到对应时间点。";
        }
        double time = Math.max(0, hit.startTime());
        context.seekTo(time);
        context.addSource(new AgentChatResponse.Source(hit.title(), hit.content(), hit.part(), time));
        String result = "已定位到第%d集 %.1f 秒：%s".formatted(hit.part() == null ? 1 : hit.part(), time, hit.content());
        context.record("seek_video", result);
        return result;
    }

    @Tool(name = "create_practice", description = "读取当前课程上下文，为学习者生成一道两选一练习题", strict = true, readOnly = true)
    public String createPractice(AgentScopeRequestContext context) {
        String result = "请基于当前字幕生成一道两选一练习题。当前字幕：" + value(context.request().current());
        context.record("create_practice", result);
        return result;
    }

    @Tool(name = "get_review_plan", description = "读取当前用户到期和薄弱的知识点，生成聚焦复习计划", strict = true, readOnly = true)
    public String getReviewPlan(AgentScopeRequestContext context) {
        var points = new LinkedHashMap<java.util.UUID, com.omnistudy.model.entity.KnowledgePoint>();
        knowledgeService.duePoints(context.userId()).forEach(point -> points.put(point.getId(), point));
        knowledgeService.weakPoints(context.userId()).forEach(point -> points.putIfAbsent(point.getId(), point));
        var selected = points.values().stream().limit(5).toList();
        if (selected.isEmpty()) {
            context.record("get_review_plan", "当前没有到期或薄弱知识点。");
            return "当前没有到期或薄弱知识点。";
        }
        StringBuilder result = new StringBuilder("待复习知识点：\n");
        selected.forEach(point -> result.append("- ").append(point.getName()).append("，掌握度 ")
                .append(Math.round((point.getMasteryScore() == null ? 0 : point.getMasteryScore()) * 100)).append("%\n"));
        context.record("get_review_plan", result.toString());
        return result.toString();
    }

    @Tool(name = "request_open_idea", description = "只生成打开 IDEA 的待确认建议，不执行本机操作", strict = true, readOnly = true)
    public String requestOpenIdea(
            @ToolParam(name = "reason", description = "为什么适合进入 IDE 动手", required = false) String reason,
            @ToolParam(name = "suggested_file", description = "建议打开的项目内相对路径", required = false) String suggestedFile,
            AgentScopeRequestContext context) {
        var handoff = new AgentChatResponse.IdeHandoff(valueOr(reason, "这个问题适合进入 IDEA 动手练习"), suggestedFile);
        context.handoff(handoff);
        context.record("request_open_idea", "已生成 IDEA 建议，等待浏览器侧用户确认；没有打开任何应用。");
        return "已生成 IDEA 建议，必须等待用户点击确认按钮。不要声称 IDEA 已经打开。";
    }

    private String value(String value) { return valueOr(value, "未提供"); }
    private String valueOr(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
