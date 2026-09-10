package com.omnistudy.agentscope;

import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import com.omnistudy.service.AgentUsageService;
import com.omnistudy.service.AiCredentialService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(prefix = "app.agent-scope", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentScopeConfiguration {
    @Bean(destroyMethod = "close")
    HarnessAgent omniStudyHarnessAgent(
            PostgresAgentStateStore stateStore,
            OmniStudyAgentScopeTools tools,
            ScreenshotContextMiddleware screenshotMiddleware,
            AgentUsageService usageService,
            AiCredentialService credentialService,
            @Value("${app.agent-scope.max-iters:3}") int maxIters,
            @Value("${app.agent-scope.max-context-tokens:12000}") int maxContextTokens,
            @Value("${app.agent-scope.compaction-trigger-messages:20}") int triggerMessages,
            @Value("${app.agent-scope.compaction-trigger-tokens:8000}") int triggerTokens,
            @Value("${app.agent-scope.compaction-keep-messages:8}") int keepMessages,
            @Value("${app.agent-scope.workspace:../../.agentscope/workspace}") String workspace,
            @Value("${app.agent-scope.skills-directory:../../skills}") String skillsDirectory) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(tools);
        var model = new UserAwareDashScopeModel(credentialService, false);
        var compactionModel = new MeteredAgentScopeModel(
                new UserAwareDashScopeModel(credentialService, true), usageService, "CONTEXT_COMPACTION");
        return HarnessAgent.builder()
                .name("omnistudy-learning-agent")
                .description("面向网课视频、笔记检索和间隔复习的受控学习 Agent")
                .sysPrompt("你是 OmniStudy 学习 Agent。必须优先调用领域工具获取事实，并严格输出约定 JSON。")
                .model(model)
                .toolkit(toolkit)
                .stateStore(stateStore)
                .workspace(Path.of(workspace).toAbsolutePath().normalize())
                .projectGlobalSkillsDir(Path.of(skillsDirectory).toAbsolutePath().normalize())
                .middleware(screenshotMiddleware)
                .compaction(CompactionConfig.builder()
                        .triggerMessages(triggerMessages)
                        .triggerTokens(triggerTokens)
                        .keepMessages(keepMessages)
                        .keepTokens(0)
                        .flushBeforeCompact(false)
                        .offloadBeforeCompact(false)
                        .model(compactionModel)
                        .build())
                .maxContextTokens(maxContextTokens)
                .maxIters(Math.max(1, Math.min(maxIters, 3)))
                .permissionContext(PermissionContextState.builder().mode(PermissionMode.EXPLORE).build())
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableSessionPersistence()
                .disableSubagents()
                .disableToolsConfig()
                .enablePlanMode(false)
                .enableAgentTracingLog(true)
                .build();
    }

}
