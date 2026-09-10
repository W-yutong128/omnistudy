package com.omnistudy.service;

import com.omnistudy.model.dto.NoteGenerateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteGenerationRequestService {
    private final NoteService noteService;
    private final AiJobQueueService queueService;
    private final AiCredentialService credentialService;

    @Transactional
    public void request(NoteGenerateRequest request, UUID userId, boolean finalize) {
        // 在创建持久化任务前快速失败；若入队后用户删除 Key，Worker 仍会终止任务且不重试。
        credentialService.resolve(userId);
        queueService.enqueueNote(UUID.fromString(request.sessionId()), userId, finalize);
        // 与入队处于同一事务；Worker 只能在任务和 generating 状态都提交后领取。
        noteService.prepareGeneration(request, userId);
    }
}
