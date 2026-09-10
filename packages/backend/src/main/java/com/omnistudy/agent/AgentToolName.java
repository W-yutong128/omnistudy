package com.omnistudy.agent;

import java.util.Arrays;

public enum AgentToolName {
    EXPLAIN_CURRENT("explain_current"), SEARCH_LEARNING_MEMORY("search_learning_memory"),
    CREATE_PRACTICE("create_practice"), SEEK_VIDEO("seek_video"),
    GET_REVIEW_PLAN("get_review_plan"), REQUEST_OPEN_IDEA("request_open_idea"), RESPOND("respond");
    private final String wireName;
    AgentToolName(String wireName) { this.wireName = wireName; }
    public String wireName() { return wireName; }
    public static AgentToolName fromWire(String value) {
        return Arrays.stream(values()).filter(item -> item.wireName.equals(value)).findFirst().orElse(RESPOND);
    }
}
