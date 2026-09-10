package com.omnistudy.service;

import com.omnistudy.ai.AiRequest;
import com.omnistudy.ai.AiTask;
import com.omnistudy.model.dto.AiProviderConnectionTestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
        try {
            meteredAiService.generate(userId, AiTask.CONNECTION_TEST, AiRequest.text(
                    "你是 API 连接检查器，只输出合法 JSON。",
                    "仅返回 {\"ok\":true}，不要添加其他内容。"));
            credentialService.markConnectionSucceeded(userId);
            return new AiProviderConnectionTestResponse(true, credential.provider(),
                    credential.strongTextModel(), Duration.between(started, Instant.now()).toMillis(),
                    "API Key 与文本模型连接正常");
        } catch (RuntimeException error) {
            credentialService.markConnectionFailed(userId, userMessage(error));
            throw error;
        }
    }

    private String userMessage(RuntimeException error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebClientResponseException response) {
                int status = response.getStatusCode().value();
                if (status == 401 || status == 403) return "API Key 无效或没有模型访问权限";
                if (status == 429) return "DashScope 请求过于频繁或账户额度不足";
                if (status == 400) return "模型名称或请求参数不受 DashScope 支持";
                return "DashScope 返回 HTTP " + status;
            }
            if (current instanceof java.util.concurrent.TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return "连接 DashScope 超时，请检查网络后重试";
            }
            current = current.getCause();
        }
        return "无法连接 DashScope，请检查网络、API Key 和模型名称";
    }
}
