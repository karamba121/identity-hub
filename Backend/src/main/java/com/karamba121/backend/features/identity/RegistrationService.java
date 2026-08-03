package com.karamba121.backend.features.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.config.IdentityHubProperties;

@Service
public class RegistrationService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SecureRandom secureRandom = new SecureRandom();
    private final IdentityUserRepository users;
    private final EmailVerificationTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailVerificationSender sender;
    private final IdentityHubProperties properties;

    public RegistrationService(
            IdentityUserRepository users,
            EmailVerificationTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            EmailVerificationSender sender,
            IdentityHubProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sender = sender;
        this.properties = properties;
    }

    @Transactional
    public void register(String email, String displayName, String password) {
        String normalizedEmail = validEmail(email);
        String normalizedName = required(displayName, "Nome", 2, 200);
        String validPassword = passwordPolicy.validate(password, normalizedEmail, normalizedName);
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            return;
        }

        IdentityUser user = users.save(IdentityUser.pendingEmailVerification(
                normalizedEmail,
                normalizedName,
                passwordEncoder.encode(validPassword)));
        String rawToken = newToken();
        tokens.save(new EmailVerificationToken(
                user,
                hash(rawToken),
                Instant.now().plus(properties.registration().verificationTtl())));
        sender.send(user.getEmail(), user.getDisplayName(), verificationUrl(rawToken));
    }

    @Transactional
    public void verify(String rawToken) {
        String normalizedToken = required(rawToken, "Token", 20, 200);
        EmailVerificationToken token = tokens.findByTokenHash(hash(normalizedToken))
                .orElseThrow(InvalidEmailVerificationTokenException::new);
        token.consume(Instant.now());
        token.getUser().verifyEmail();
    }

    private String verificationUrl(String rawToken) {
        String baseUrl = properties.uiBaseUrl().replaceAll("/+$", "");
        return baseUrl + "/verify-email#token=" + rawToken;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String validEmail(String value) {
        String email = required(value, "E-mail", 3, 254).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("E-mail inválido");
        }
        return email;
    }

    private static String required(String value, String field, int minLength, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        String normalized = value.trim();
        if (normalized.length() < minLength || normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " deve conter entre " + minLength + " e " + maxLength + " caracteres");
        }
        return normalized;
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
