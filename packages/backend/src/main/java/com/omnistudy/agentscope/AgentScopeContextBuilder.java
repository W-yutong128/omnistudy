package com.omnistudy.agentscope;

import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.entity.StudySession;
import org.springframework.stereotype.Component;

@Component
public class AgentScopeContextBuilder {
    private static final int MAX_PROMPT_CHARS = 24_000;

    public ContextPayload build(AgentChatRequest request, StudySession session, String skillInstructions) {
        String prompt = """
                <current_learning_context>
                课程：%s
                分集：%d
                播放时间：%.1f 秒
                前文字幕：%s
                当前字幕：%s
                后文字幕：%s
                </current_learning_context>

                <active_skill>
                %s
                </active_skill>

                <user_request>
                %s
                </user_request>

                根据需要调用且只调用已注册的 OmniStudy 工具。最终回答必须基于工具结果。
                最终只输出 JSON：{"reply":"回答内容","practice":null}
                如果生成练习，practice 格式为：
                {"question":"题目","options":[{"id":"A","text":"选项"},{"id":"B","text":"选项"}],"correctOptionId":"A","explanation":"解析"}
                """.formatted(value(session.getVideoTitle()), request.part() == null ? 1 : request.part(),
                request.currentTime() == null ? 0 : request.currentTime(), truncate(request.before(), 2500),
                truncate(request.current(), 3500), truncate(request.after(), 2500),
                truncate(skillInstructions, 2500), truncate(request.message(), 4000));
        String bounded = truncate(prompt, MAX_PROMPT_CHARS);
        return new ContextPayload(bounded, estimateTokens(bounded), request.screenshot() != null && !request.screenshot().isBlank());
    }

    int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int ascii = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) < 128) ascii++;
        int nonAscii = text.length() - ascii;
        return Math.max(1, (int) Math.ceil(ascii / 4.0 + nonAscii / 1.5));
    }

    private String value(String value) { return value == null || value.isBlank() ? "未提供" : value; }
    private String truncate(String value, int max) {
        String safe = value(value);
        return safe.length() <= max ? safe : safe.substring(0, max) + "…";
    }

    public record ContextPayload(String prompt, int estimatedInputTokens, boolean screenshotAvailable) {}
}
