package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.agent.AgentFallbackRouter;
import com.omnistudy.agent.AgentSkillRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRoutingEvalTest {
    @Test
    void routingDatasetPasses() throws Exception {
        var mapper = new ObjectMapper();
        var router = new AgentFallbackRouter();
        Path dataset = Path.of("../../evals/agent-routing.jsonl").normalize();
        int total = 0;
        for (String line : Files.readAllLines(dataset)) {
            if (line.isBlank()) continue;
            var item = mapper.readTree(line);
            assertEquals(item.path("expectedTool").asText(),
                    router.route(item.path("input").asText()).wireName(), item.path("input").asText());
            total++;
        }
        assertEquals(13, total);
    }

    @Test
    void exposesVersionedChineseSkillContracts() {
        var registry = new AgentSkillRegistry();
        var tutor = registry.select("解释老师刚才讲的内容");
        var review = registry.select("帮我复习薄弱知识点");
        var coding = registry.select("分析这段 Java 代码报错");

        assertEquals("current-course-tutor", tutor.name());
        assertEquals("review-coach", review.name());
        assertEquals("coding-course-coach", coding.name());
        assertEquals("1.1.0", tutor.version());
        assertEquals("1.1.0", review.version());
        assertEquals("1.1.0", coding.version());
    }
}
