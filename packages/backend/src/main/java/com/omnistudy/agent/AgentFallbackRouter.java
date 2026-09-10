package com.omnistudy.agent;

import org.springframework.stereotype.Component;

@Component
public class AgentFallbackRouter {
    public AgentToolName route(String message) {
        String text = message == null ? "" : message;
        return text.matches(".*(打开|启动|进入).{0,8}(IDEA|idea|IntelliJ|编辑器).*") ? AgentToolName.REQUEST_OPEN_IDEA
                : text.matches(".*(复习计划|复习什么|薄弱|待复习|到期知识点).*") ? AgentToolName.GET_REVIEW_PLAN
                : text.matches(".*(练习|出.{0,3}题|测试我).*") ? AgentToolName.CREATE_PRACTICE
                : text.matches(".*(跳到|回到|定位|哪.{0,2}讲).*") ? AgentToolName.SEEK_VIDEO
                : text.matches(".*(笔记|以前学过|搜索|查找).*") ? AgentToolName.SEARCH_LEARNING_MEMORY
                : text.matches(".*(解释|没.{0,2}听懂|什么意思|讲一下).*") ? AgentToolName.EXPLAIN_CURRENT
                : AgentToolName.RESPOND;
    }
}
