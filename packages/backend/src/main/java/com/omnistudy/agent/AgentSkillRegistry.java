package com.omnistudy.agent;

import org.springframework.stereotype.Component;

@Component
public class AgentSkillRegistry {
    private static final AgentSkill TUTOR = new AgentSkill("current-course-tutor", "1.0.0",
            "基于当前字幕、截图和检索来源解释；证据不足时说明；来源使用[1][2]。可用一句检查理解的问题收尾。");
    private static final AgentSkill REVIEW = new AgentSkill("review-coach", "1.0.0",
            "优先处理到期、低掌握度和重复错误知识点；每次聚焦3到5项；答题前不要直接泄露答案。");
    private static final AgentSkill CODING = new AgentSkill("coding-course-coach", "1.0.0",
            "先诊断理解和当前尝试，再渐进提示；除非明确要求，否则不要给完整代码；打开IDE前必须确认目标。");

    public AgentSkill select(String message) {
        String text = message == null ? "" : message.toLowerCase();
        if (text.matches(".*(代码|编程|报错|异常|bug|debug|idea|java|python|typescript).*")) return CODING;
        if (text.matches(".*(复习|薄弱|掌握|错题|计划|到期).*")) return REVIEW;
        return TUTOR;
    }

    public record AgentSkill(String name, String version, String instructions) {}
}
