package com.omnistudy.service;

import com.omnistudy.ai.AiModelRouter;
import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiResult;
import com.omnistudy.ai.AiTask;
import com.omnistudy.exception.QuotaExceededException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MeteredAiServiceTest {
    private final AiModelRouter router = mock(AiModelRouter.class);
    private final AgentUsageService usage = mock(AgentUsageService.class);
    private final MeteredAiService service = new MeteredAiService(router, usage);

    @Test
    void reservesQuotaAndStoresProviderTokenUsage() {
        UUID userId = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        AiRequest request = AiRequest.text("system", "用户问题");
        when(usage.begin(eq(userId), anyInt(), eq("EVALUATE_ANSWER"))).thenReturn(reservation);
        when(router.generateResult(userId, AiTask.EVALUATE_ANSWER, request))
                .thenReturn(new AiResult("answer", 120, 30, 10));

        assertEquals("answer", service.generate(userId, AiTask.EVALUATE_ANSWER, request));

        verify(usage).finish(reservation, 120, 30, 10, true);
    }

    @Test
    void recordsFailedProviderCall() {
        UUID userId = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        AiRequest request = AiRequest.text("system", "prompt");
        when(usage.begin(eq(userId), anyInt(), eq("NOTE_FINALIZE"))).thenReturn(reservation);
        when(router.generateResult(userId, AiTask.NOTE_FINALIZE, request)).thenThrow(new IllegalStateException("upstream"));

        assertThrows(IllegalStateException.class,
                () -> service.generate(userId, AiTask.NOTE_FINALIZE, request));

        verify(usage).finish(reservation, 0, 0, 0, false);
    }

    @Test
    void doesNotCallProviderWhenQuotaIsExhausted() {
        UUID userId = UUID.randomUUID();
        AiRequest request = AiRequest.vision("system", "prompt", "image");
        when(usage.begin(eq(userId), anyInt(), eq("INTERCEPT")))
                .thenThrow(new QuotaExceededException("额度不足"));

        assertThrows(QuotaExceededException.class,
                () -> service.generate(userId, AiTask.INTERCEPT, request));

        verify(router, never()).generateResult(any(), any(), any());
    }

    @Test
    void doesNotReserveUsageWithoutAnAvailableCredential() {
        UUID userId = UUID.randomUUID();
        AiRequest request = AiRequest.text("system", "prompt");
        doThrow(new com.omnistudy.exception.MissingAiCredentialException("请配置 Key"))
                .when(router).requireCredential(userId);

        assertThrows(com.omnistudy.exception.MissingAiCredentialException.class,
                () -> service.generate(userId, AiTask.EVALUATE_ANSWER, request));

        verifyNoInteractions(usage);
    }

    @Test
    void reusesOperationKeyForIncrementalNoteCalls() {
        UUID userId = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        AiRequest request = AiRequest.text("system", "incremental note");
        when(usage.beginOperation(eq(userId), anyInt(), eq("NOTE_CHUNK_SUMMARY"),
                eq("NOTE_SESSION:123"))).thenReturn(reservation);
        when(router.generateResult(userId, AiTask.NOTE_CHUNK_SUMMARY, request))
                .thenReturn(new AiResult("summary", 80, 20, 0));

        assertEquals("summary", service.generateForOperation(
                userId, AiTask.NOTE_CHUNK_SUMMARY, request, "NOTE_SESSION:123"));

        verify(usage).finish(reservation, 80, 20, 0, true);
    }
}
