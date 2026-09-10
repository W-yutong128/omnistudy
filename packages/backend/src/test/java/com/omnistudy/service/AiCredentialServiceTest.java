package com.omnistudy.service;

import com.omnistudy.model.dto.AiProviderSettingsRequest;
import com.omnistudy.exception.MissingAiCredentialException;
import com.omnistudy.model.entity.UserAiCredential;
import com.omnistudy.repository.UserAiCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiCredentialServiceTest {
    private final UserAiCredentialRepository repository = mock(UserAiCredentialRepository.class);
    private final AiCredentialCrypto crypto = new AiCredentialCrypto("test-encryption-key-that-is-long-enough");
    private final AiCredentialService service = new AiCredentialService(repository, crypto);

    @BeforeEach
    void configure() {
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.test/v1");
        ReflectionTestUtils.setField(service, "defaultFastVisionModel", "vision-default");
        ReflectionTestUtils.setField(service, "defaultStrongTextModel", "text-default");
    }

    @Test
    void encryptsAndResolvesUserKeyWithoutReturningPlaintext() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any(UserAiCredential.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.save(userId,
                new AiProviderSettingsRequest("sk-user-secret-9876", "vision-custom", "text-custom"));
        UserAiCredential stored = mockingDetails(repository).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("save"))
                .map(invocation -> (UserAiCredential) invocation.getArgument(0)).findFirst().orElseThrow();

        assertEquals("****9876", response.maskedApiKey());
        assertFalse(stored.getKeyCiphertext().contains("sk-user-secret"));
        when(repository.findById(userId)).thenReturn(Optional.of(stored));
        assertEquals("sk-user-secret-9876", service.resolve(userId).apiKey());
        assertEquals("vision-custom", service.resolve(userId).fastVisionModel());
    }

    @Test
    void requiresUserKeyWhenUserHasNoCredential() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());

        assertFalse(service.settings(userId).configured());
        assertEquals("NONE", service.settings(userId).source());
        assertThrows(MissingAiCredentialException.class, () -> service.resolve(userId));
    }

    @Test
    void authenticatedEncryptionRejectsTampering() {
        var encrypted = crypto.encrypt("secret");
        String tampered = encrypted.ciphertext().substring(0, encrypted.ciphertext().length() - 2) + "AA";
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(tampered, encrypted.iv()));
    }
}
