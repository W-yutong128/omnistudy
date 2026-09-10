package com.omnistudy.ai;

import com.omnistudy.service.AiCredentialService;

public interface AiModelAdapter {
    String provider();
    AiResult generate(ModelRoute route, AiRequest request, AiCredentialService.ResolvedCredential credential);
}
