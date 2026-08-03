package com.karamba121.backend.features.identity;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MfaSecretProtector {

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public MfaSecretProtector(@Value("${identity-hub.mfa.encryption-key}") String encodedKey) {
        byte[] decoded = Base64.getUrlDecoder().decode(encodedKey);
        if (decoded.length != 32) throw new IllegalStateException("A chave de MFA deve possuir 32 bytes");
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String protect(String secret) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível proteger o segredo MFA", exception);
        }
    }

    public String reveal(String protectedSecret) {
        try {
            byte[] value = Base64.getUrlDecoder().decode(protectedSecret);
            byte[] nonce = java.util.Arrays.copyOfRange(value, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(value, 12, value.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.US_ASCII);
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível abrir o segredo MFA", exception);
        }
    }
}
