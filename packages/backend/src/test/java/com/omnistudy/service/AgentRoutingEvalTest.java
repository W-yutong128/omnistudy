package com.omnistudy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnistudy.agent.AgentFallbackRouter;
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
}
