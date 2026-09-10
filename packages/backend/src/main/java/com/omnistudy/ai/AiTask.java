package com.omnistudy.ai;

/** 业务只声明任务，不直接依赖具体厂商或模型名称。 */
public enum AiTask {
    CONNECTION_TEST,
    INTERCEPT,
    EVALUATE_ANSWER,
    NOTE_CHUNK_SUMMARY,
    NOTE_FINALIZE,
    STUDY_MATERIAL,
    AGENT_PLAN,
    AGENT_REPLY
}
