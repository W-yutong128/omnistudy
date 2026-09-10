package com.omnistudy.service;

import com.omnistudy.exception.MissingAiCredentialException;
import com.omnistudy.model.dto.AiProviderSettingsRequest;
import com.omnistudy.model.dto.AiProviderSettingsResponse;
import com.omnistudy.model.entity.UserAiCredential;
import com.omnistudy.repository.UserAiCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiCredentialService {
    private final UserAiCredentialRepository repository;
    private final AiCredentialCrypto crypto;

    @Value("${app.ai.providers.dashscope.base-url}") private String baseUrl;
    @Value("${app.ai.models.fast-vision:qwen3-vl-flash}") private String defaultFastVisionModel;
    @Value("${app.ai.models.strong-text:qwen-plus}") private String defaultStrongTextModel;

    @Transactional(readOnly = true)
    public AiProviderSettingsResponse settings(UUID userId) {
        return repository.findById(userId).map(value -> response(value, "USER"))
                .orElseGet(this::unconfiguredSettings);
    }

    @Transactional
    public AiProviderSettingsResponse save(UUID userId, AiProviderSettingsRequest request) {
        String rawKey = request.apiKey().trim();
        AiCredentialCrypto.EncryptedValue encrypted = crypto.encrypt(rawKey);
        UserAiCredential value = repository.findById(userId).orElseGet(() -> UserAiCredential.builder()
                .userId(userId).createdAt(OffsetDateTime.now()).build());
        value.setProvider("dashscope");
        value.setKeyCiphertext(encrypted.ciphertext());
        value.setKeyIv(encrypted.iv());
        value.setKeyHint(hint(rawKey));
        value.setFastVisionModel(normalizeModel(request.fastVisionModel(), defaultFastVisionModel));
        value.setStrongTextModel(normalizeModel(request.strongTextModel(), defaultStrongTextModel));
        value.setUpdatedAt(OffsetDateTime.now());
        return response(repository.save(value), "USER");
    }

    @Transactional
    public AiProviderSettingsResponse delete(UUID userId) {
        repository.deleteById(userId);
        return unconfiguredSettings();
    }

    @Transactional(readOnly = true)
    public ResolvedCredential resolve(UUID userId) {
        return repository.findById(userId)
                .map(value -> new ResolvedCredential("dashscope", baseUrl,
                        crypto.decrypt(value.getKeyCiphertext(), value.getKeyIv()),
                        normalizeModel(value.getFastVisionModel(), defaultFastVisionModel),
                        normalizeModel(value.getStrongTextModel(), defaultStrongTextModel), "USER"))
                .orElseThrow(() -> new MissingAiCredentialException("请先在“模型”页面配置自己的 DashScope API Key"));
    }

    private AiProviderSettingsResponse response(UserAiCredential value, String source) {
        return new AiProviderSettingsResponse(true, source, value.getProvider(), baseUrl,
                "****" + value.getKeyHint(),
                normalizeModel(value.getFastVisionModel(), defaultFastVisionModel),
                normalizeModel(value.getStrongTextModel(), defaultStrongTextModel));
    }

    private AiProviderSettingsResponse unconfiguredSettings() {
        return new AiProviderSettingsResponse(false, "NONE", "dashscope", baseUrl, null,
                defaultFastVisionModel, defaultStrongTextModel);
    }

    private String normalizeModel(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
    private String hint(String value) { return value.substring(Math.max(0, value.length() - 4)); }
    public record ResolvedCredential(String provider, String baseUrl, String apiKey,
                                     String fastVisionModel, String strongTextModel, String source) {}
}
