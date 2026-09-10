package com.omnistudy.agentscope;

import com.omnistudy.model.dto.AgentChatRequest;
import com.omnistudy.model.dto.AgentChatResponse;
import com.omnistudy.model.entity.StudySession;
import com.omnistudy.agent.AgentSkillRegistry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AgentScopeRequestContext {
    private final UUID userId;
    private final StudySession session;
    private final AgentChatRequest request;
    private final AgentSkillRegistry.AgentSkill skill;
    private final AtomicReference<String> tool = new AtomicReference<>("respond");
    private final AtomicInteger steps = new AtomicInteger();
    private final CopyOnWriteArrayList<AgentChatResponse.Source> sources = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> observations = new CopyOnWriteArrayList<>();
    private final AtomicReference<Double> seekTo = new AtomicReference<>();
    private final AtomicReference<AgentChatResponse.IdeHandoff> ideHandoff = new AtomicReference<>();
    private final AtomicBoolean screenshotInjected = new AtomicBoolean();

    public AgentScopeRequestContext(UUID userId, StudySession session, AgentChatRequest request,
                                    AgentSkillRegistry.AgentSkill skill) {
        this.userId = userId;
        this.session = session;
        this.request = request;
        this.skill = skill;
    }

    public UUID userId() { return userId; }
    public StudySession session() { return session; }
    public AgentChatRequest request() { return request; }
    public AgentSkillRegistry.AgentSkill skill() { return skill; }
    public String tool() { return tool.get(); }
    public int steps() { return steps.get(); }
    public List<AgentChatResponse.Source> sources() { return List.copyOf(sources); }
    public String observation() { return String.join("\n", observations); }
    public Double seekTo() { return seekTo.get(); }
    public AgentChatResponse.IdeHandoff ideHandoff() { return ideHandoff.get(); }
    public boolean hasInjectedScreenshot() { return screenshotInjected.get(); }

    public void record(String toolName, String observation) {
        tool.set(toolName);
        steps.incrementAndGet();
        if (observation != null && !observation.isBlank()) observations.add(observation);
    }
    public void addSource(AgentChatResponse.Source source) { sources.add(source); }
    public void seekTo(Double value) { seekTo.set(value); }
    public void handoff(AgentChatResponse.IdeHandoff value) { ideHandoff.set(value); }
    public void screenshotInjected() { screenshotInjected.set(true); }
}
