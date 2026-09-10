package com.omnistudy.service;

import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiTask;
import com.omnistudy.model.dto.AiProviderConnectionTestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiProviderConnectionService {
    private final AiCredentialService credentialService;
    private final MeteredAiService meteredAiService;

    public AiProviderConnectionTestResponse test(UUID userId) {
        AiCredentialService.ResolvedCredential credential = credentialService.resolve(userId);
        Instant started = Instant.now();
        meteredAiService.generate(userId, AiTask.CONNECTION_TEST, AiRequest.text(
                "你是 API 连接检查器，只输出合法 JSON。",
                "仅返回 {\"ok\":true}，不要添加其他内容。"));
        return new AiProviderConnectionTestResponse(true, credential.provider(),
                credential.strongTextModel(), Duration.between(started, Instant.now()).toMillis(),
                "API Key 与文本模型连接正常");
    }
}
