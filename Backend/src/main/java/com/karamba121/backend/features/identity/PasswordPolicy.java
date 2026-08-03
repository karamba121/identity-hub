package com.karamba121.backend.features.identity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class PasswordPolicy {

    public static final int MIN_LENGTH = 15;
    public static final int MAX_LENGTH = 128;

    private static final Set<String> BLOCKED = Set.of(
            "123456", "12345678", "123456789", "1234567890", "111111111111111",
            "abc123", "admin", "administrator", "changeme", "iloveyou", "letmein",
            "password", "password1", "password123", "password1234567", "passwordpassword",
            "qwerty", "qwerty123", "qwerty123456789", "senha", "senha123", "senha1234567890",
            "welcome", "welcome123", "welcome12345678");

    public String validate(String password, String email, String displayName) {
        if (password == null) {
            throw new IllegalArgumentException("Senha é obrigatória");
        }
        String normalized = normalize(password);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Senha deve conter entre " + MIN_LENGTH + " e " + MAX_LENGTH + " caracteres");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Senha não pode conter caracteres de controle");
        }

        String compact = compact(normalized);
        if (BLOCKED.contains(compact)
                || compact.contains("identityhub")
                || normalized.codePoints().distinct().limit(2).count() == 1) {
            throw new IllegalArgumentException("A senha escolhida é muito comum ou previsível");
        }
        if (containsContext(compact, emailLocalPart(email))
                || displayNameTerms(displayName).stream().anyMatch(term -> containsContext(compact, term))) {
            throw new IllegalArgumentException("Senha não deve conter seu nome ou identificador de e-mail");
        }
        return normalized;
    }

    public static String normalize(String password) {
        return password == null ? null : Normalizer.normalize(password, Normalizer.Form.NFC);
    }

    private static boolean containsContext(String password, String context) {
        String normalizedContext = compact(context);
        return normalizedContext.length() >= 4 && password.contains(normalizedContext);
    }

    private static String emailLocalPart(String email) {
        if (email == null) {
            return "";
        }
        int separator = email.indexOf('@');
        return separator < 0 ? email : email.substring(0, separator);
    }

    private static Set<String> displayNameTerms(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(displayName.split("\\s+"))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String compact(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}]", "")
                .toLowerCase(Locale.ROOT);
    }
}
