package com.omnistudy.agent;
public record AgentToolSpec(AgentToolName name, String description, String inputSchema,
                            boolean readOnly, boolean requiresConfirmation, int timeoutMs) {}
