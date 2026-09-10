package com.omnistudy.service;

import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiTask;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiProviderConnectionServiceTest {
    @Test
    void testsTheSignedInUsersStoredCredential() {
        AiCredentialService credentials = mock(AiCredentialService.class);
        MeteredAiService ai = mock(MeteredAiService.class);
        AiProviderConnectionService service = new AiProviderConnectionService(credentials, ai);
        UUID userId = UUID.randomUUID();
        when(credentials.resolve(userId)).thenReturn(new AiCredentialService.ResolvedCredential(
                "dashscope", "https://example.test/v1", "secret", "vision", "qwen-plus", "USER"));
        when(ai.generate(eq(userId), eq(AiTask.CONNECTION_TEST), any(AiRequest.class)))
                .thenReturn("{\"ok\":true}");

        var result = service.test(userId);

        assertTrue(result.reachable());
        assertEquals("dashscope", result.provider());
        assertEquals("qwen-plus", result.model());
        assertTrue(result.latencyMs() >= 0);
        verify(ai).generate(eq(userId), eq(AiTask.CONNECTION_TEST), any(AiRequest.class));
    }
}
