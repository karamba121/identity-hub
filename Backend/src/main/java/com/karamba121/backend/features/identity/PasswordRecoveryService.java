package com.karamba121.backend.features.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.config.IdentityHubProperties;

@Service
public class PasswordRecoveryService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final IdentityUserRepository users;
    private final PasswordRecoveryTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final PasswordRecoverySender sender;
    private final IdentityHubProperties properties;

    public PasswordRecoveryService(
            IdentityUserRepository users,
            PasswordRecoveryTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            PasswordRecoverySender sender,
            IdentityHubProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sender = sender;
        this.properties = properties;
    }

    @Transactional
    public void request(String email) {
        String normalizedEmail = normalizeEmail(email);
        users.findByEmailIgnoreCase(normalizedEmail).ifPresent(candidate -> {
            IdentityUser user = users.findByIdForUpdate(candidate.getId()).orElse(null);
            if (user == null || !user.isEnabled() || !user.isEmailVerified()) {
                return;
            }

            Instant now = Instant.now();
            tokens.revokeActiveByUserId(user.getId(), now);
            String rawToken = newToken();
            tokens.saveAndFlush(new PasswordRecoveryToken(
                    user,
                    hash(rawToken),
                    now.plus(properties.passwordRecovery().tokenTtl())));
            sender.send(user.getEmail(), user.getDisplayName(), recoveryUrl(rawToken));
        });
    }

    @Transactional
    public void complete(String rawToken, String newPassword) {
        String tokenValue = requiredToken(rawToken);
        String tokenHash = hash(tokenValue);
        String userId = tokens.findUserIdByTokenHash(tokenHash)
                .orElseThrow(InvalidPasswordRecoveryTokenException::new);
        IdentityUser user = users.findByIdForUpdate(userId)
                .orElseThrow(InvalidPasswordRecoveryTokenException::new);
        PasswordRecoveryToken token = tokens.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(InvalidPasswordRecoveryTokenException::new);

        String validPassword = passwordPolicy.validate(newPassword, user.getEmail(), user.getDisplayName());
        Instant now = Instant.now();
        token.consume(now);
        user.updatePasswordHash(passwordEncoder.encode(validPassword));
        user.resetLoginFailures();
        tokens.revokeActiveByUserId(user.getId(), now);
    }

    private String recoveryUrl(String rawToken) {
        String baseUrl = properties.uiBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/recover-password#token=" + rawToken;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("E-mail é obrigatório");
        }
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("E-mail inválido");
        }
        return email;
    }

    private static String requiredToken(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidPasswordRecoveryTokenException();
        }
        String token = value.trim();
        if (token.length() < 20 || token.length() > 200) {
            throw new InvalidPasswordRecoveryTokenException();
        }
        return token;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
