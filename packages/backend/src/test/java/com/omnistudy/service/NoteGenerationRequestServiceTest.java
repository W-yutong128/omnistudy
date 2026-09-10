package com.omnistudy.service;

import com.omnistudy.model.dto.NoteGenerateRequest;
import com.omnistudy.exception.MissingAiCredentialException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.mockito.Mockito.*;

class NoteGenerationRequestServiceTest {
    @Test
    void persistsJobBeforePublishingGeneratingStateInSameTransaction() {
        NoteService notes = mock(NoteService.class);
        AiJobQueueService queue = mock(AiJobQueueService.class);
        AiCredentialService credentials = mock(AiCredentialService.class);
        NoteGenerationRequestService service = new NoteGenerationRequestService(notes, queue, credentials);
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        NoteGenerateRequest request = new NoteGenerateRequest(sessionId.toString());

        service.request(request, userId, true);

        InOrder order = inOrder(credentials, queue, notes);
        order.verify(credentials).resolve(userId);
        order.verify(queue).enqueueNote(sessionId, userId, true);
        order.verify(notes).prepareGeneration(request, userId);
    }

    @Test
    void missingKeyDoesNotCreateAJob() {
        NoteService notes = mock(NoteService.class);
        AiJobQueueService queue = mock(AiJobQueueService.class);
        AiCredentialService credentials = mock(AiCredentialService.class);
        NoteGenerationRequestService service = new NoteGenerationRequestService(notes, queue, credentials);
        UUID userId = UUID.randomUUID();
        NoteGenerateRequest request = new NoteGenerateRequest(UUID.randomUUID().toString());
        doThrow(new MissingAiCredentialException("请配置 Key")).when(credentials).resolve(userId);

        org.junit.jupiter.api.Assertions.assertThrows(MissingAiCredentialException.class,
                () -> service.request(request, userId, false));

        verifyNoInteractions(queue, notes);
    }
}
