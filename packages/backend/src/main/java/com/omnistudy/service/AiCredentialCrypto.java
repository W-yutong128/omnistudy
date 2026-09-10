package com.omnistudy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AiCredentialCrypto {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AiCredentialCrypto(@Value("${app.ai.credentials.encryption-key}") String encryptionKey) {
        if (encryptionKey == null || encryptionKey.length() < 32) {
            throw new IllegalStateException("AI_CREDENTIAL_ENCRYPTION_KEY 至少需要 32 个字符");
        }
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(derived, "AES");
        } catch (Exception error) {
            throw new IllegalStateException("无法初始化 AI 凭证加密", error);
        }
    }

    public EncryptedValue encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new EncryptedValue(
                    Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8))),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception error) {
            throw new IllegalStateException("AI 凭证加密失败", error);
        }
    }

    public String decrypt(String ciphertext, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(iv)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("AI 凭证无法解密，请重新保存 API Key", error);
        }
    }

    public record EncryptedValue(String ciphertext, String iv) {}
}

