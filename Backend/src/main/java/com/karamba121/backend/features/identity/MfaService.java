package com.karamba121.backend.features.identity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.session.CriticalSessionInvalidationService;

@Service
public class MfaService {

    private static final int RECOVERY_CODE_COUNT = 8;
    private final SecureRandom random = new SecureRandom();
    private final IdentityUserRepository users;
    private final UserMfaRepository mfaRepository;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final MfaSecretProtector protector;
    private final CriticalSessionInvalidationService sessionInvalidation;

    public MfaService(
            IdentityUserRepository users,
            UserMfaRepository mfaRepository,
            MfaRecoveryCodeRepository recoveryCodes,
            MfaSecretProtector protector,
            CriticalSessionInvalidationService sessionInvalidation) {
        this.users = users;
        this.mfaRepository = mfaRepository;
        this.recoveryCodes = recoveryCodes;
        this.protector = protector;
        this.sessionInvalidation = sessionInvalidation;
    }

    @Transactional(readOnly = true)
    public Status status(String email) {
        IdentityUser user = requireUser(email);
        UserMfa mfa = mfaRepository.findById(user.getId()).orElse(null);
        return new Status(mfa != null && mfa.isEnabled(),
                recoveryCodes.countByUserIdAndUsedAtIsNull(user.getId()));
    }

    @Transactional
    public Enrollment startEnrollment(String email) {
        IdentityUser user = users.findByEmailForUpdate(email).orElseThrow();
        UserMfa current = mfaRepository.findByUserIdForUpdate(user.getId()).orElse(null);
        if (current != null && current.isEnabled()) {
            throw new IllegalStateException("MFA já está habilitado");
        }
        if (current != null) mfaRepository.delete(current);
        recoveryCodes.deleteByUserId(user.getId());

        byte[] entropy = new byte[20];
        random.nextBytes(entropy);
        String secret = TotpAlgorithm.encodeBase32(entropy);
        mfaRepository.save(new UserMfa(user.getId(), protector.protect(secret)));
        String label = URLEncoder.encode("Identity Hub:" + user.getEmail(), StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode("Identity Hub", StandardCharsets.UTF_8);
        String uri = "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + issuer + "&algorithm=SHA1&digits=6&period=30";
        return new Enrollment(secret, uri);
    }

    @Transactional
    public List<String> confirmEnrollment(String email, String code) {
        IdentityUser user = users.findByEmailForUpdate(email).orElseThrow();
        UserMfa mfa = mfaRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalStateException("Inicie a configuração do MFA"));
        if (mfa.isEnabled()) throw new IllegalStateException("MFA já está habilitado");
        Long step = matchingStep(mfa, code, Instant.now(), false);
        if (step == null) throw new IllegalArgumentException("Código TOTP inválido");
        mfa.enable(Instant.now());
        mfa.useStep(step);
        List<String> codes = replaceRecoveryCodes(user.getId());
        user.advanceCredentialVersion();
        sessionInvalidation.invalidateForCriticalEvent(user.getEmail());
        return codes;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(String email) {
        return users.findByEmailIgnoreCase(email)
                .flatMap(user -> mfaRepository.findById(user.getId()))
                .map(UserMfa::isEnabled)
                .orElse(false);
    }

    @Transactional
    public boolean verifyChallenge(String email, String code) {
        IdentityUser user = users.findByEmailForUpdate(email).orElse(null);
        if (user == null) return false;
        UserMfa mfa = mfaRepository.findByUserIdForUpdate(user.getId()).orElse(null);
        if (mfa == null || !mfa.isEnabled()) return false;
        Long step = matchingStep(mfa, code, Instant.now(), true);
        if (step != null) {
            mfa.useStep(step);
            return true;
        }
        return recoveryCodes.findAvailableForUpdate(user.getId(), hash(normalizeRecoveryCode(code)))
                .map(recoveryCode -> {
                    recoveryCode.use(Instant.now());
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public List<String> regenerateRecoveryCodes(String email, String currentCode) {
        IdentityUser user = requireUser(email);
        if (!verifyChallenge(email, currentCode)) throw new IllegalArgumentException("Código MFA inválido");
        return replaceRecoveryCodes(user.getId());
    }

    @Transactional
    public void disable(String email, String currentCode) {
        IdentityUser user = users.findByEmailForUpdate(email).orElseThrow();
        if (!verifyChallenge(email, currentCode)) throw new IllegalArgumentException("Código MFA inválido");
        mfaRepository.deleteById(user.getId());
        recoveryCodes.deleteByUserId(user.getId());
        user.advanceCredentialVersion();
        sessionInvalidation.invalidateForCriticalEvent(user.getEmail());
    }

    private Long matchingStep(UserMfa mfa, String code, Instant now, boolean enforceReplayProtection) {
        String candidate = code == null ? "" : code.replaceAll("\\s", "");
        if (!candidate.matches("\\d{6}")) return null;
        String secret = protector.reveal(mfa.getEncryptedSecret());
        long current = now.getEpochSecond() / 30;
        for (long step = current - 1; step <= current + 1; step++) {
            if (MessageDigest.isEqual(
                    TotpAlgorithm.generateForStep(secret, step).getBytes(StandardCharsets.US_ASCII),
                    candidate.getBytes(StandardCharsets.US_ASCII))) {
                if (enforceReplayProtection && mfa.getLastUsedStep() != null && step <= mfa.getLastUsedStep()) {
                    return null;
                }
                return step;
            }
        }
        return null;
    }

    private List<String> replaceRecoveryCodes(String userId) {
        recoveryCodes.deleteByUserId(userId);
        List<String> rawCodes = new ArrayList<>();
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            byte[] entropy = new byte[10];
            random.nextBytes(entropy);
            String compact = TotpAlgorithm.encodeBase32(entropy);
            String formatted = compact.substring(0, 8) + "-" + compact.substring(8);
            rawCodes.add(formatted);
            recoveryCodes.save(new MfaRecoveryCode(userId, hash(compact)));
        }
        return List.copyOf(rawCodes);
    }

    private IdentityUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Identidade não encontrada"));
    }

    private static String normalizeRecoveryCode(String value) {
        return value == null ? "" : value.replace("-", "").replace(" ", "").toUpperCase();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    public record Status(boolean enabled, long recoveryCodesRemaining) { }
    public record Enrollment(String secret, String otpauthUri) { }
}
