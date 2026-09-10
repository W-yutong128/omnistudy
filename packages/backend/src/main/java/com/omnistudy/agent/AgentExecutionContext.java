package com.omnistudy.agent;
import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.entity.StudySession;
import java.util.UUID;
public record AgentExecutionContext(UUID userId, StudySession session, AgentChatRequest request) {}
