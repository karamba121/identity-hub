package com.karamba121.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class SigningKeyProviderTests {

    private final Path temporaryDirectory = Path.of(
            "target", "signing-key-tests", UUID.randomUUID().toString());

    @Test
    void generatedSourceCreatesEphemeralRsaKeysWithDistinctKeyIds() {
        GeneratedSigningKeyProvider provider = new GeneratedSigningKeyProvider();

        var first = provider.load();
        var second = provider.load();

        assertThat(first.current().isPrivate()).isTrue();
        assertThat(first.current().size()).isGreaterThanOrEqualTo(2048);
        assertThat(first.current().getKeyID()).isNotBlank().isNotEqualTo(second.current().getKeyID());
        assertThat(first.current().toPublicJWK()).isNotEqualTo(second.current().toPublicJWK());
    }

    @Test
    void pemSourceLoadsStableMatchingPairAndDerivesStableKeyId() throws Exception {
        KeyPair pair = keyPair(2048);
        Path privateKey = pem("signing-private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicKey = pem("signing-public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        SigningKeyProperties properties = pemProperties(privateKey, publicKey, null);

        var first = new PemSigningKeyProvider(properties, new DefaultResourceLoader()).load();
        var second = new PemSigningKeyProvider(properties, new DefaultResourceLoader()).load();

        assertThat(first.current().isPrivate()).isTrue();
        assertThat(first.current().getKeyID())
                .isEqualTo(first.current().toPublicJWK().computeThumbprint().toString());
        assertThat(second.current().getKeyID()).isEqualTo(first.current().getKeyID());
        assertThat(second.current().toPublicJWK()).isEqualTo(first.current().toPublicJWK());
    }

    @Test
    void pemSourceHonorsExplicitKeyId() throws Exception {
        KeyPair pair = keyPair(2048);
        Path privateKey = pem("explicit-private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicKey = pem("explicit-public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());

        var key = new PemSigningKeyProvider(
                pemProperties(privateKey, publicKey, "production-key-2026-08"),
                new DefaultResourceLoader()).load();

        assertThat(key.current().getKeyID()).isEqualTo("production-key-2026-08");
    }

    @Test
    void pemSourceFailsClosedForMismatchedPair() throws Exception {
        KeyPair privatePair = keyPair(2048);
        KeyPair publicPair = keyPair(2048);
        Path privateKey = pem("mismatch-private.pem", "PRIVATE KEY", privatePair.getPrivate().getEncoded());
        Path publicKey = pem("mismatch-public.pem", "PUBLIC KEY", publicPair.getPublic().getEncoded());

        assertThatThrownBy(() -> new PemSigningKeyProvider(
                pemProperties(privateKey, publicKey, null),
                new DefaultResourceLoader()).load())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Não foi possível carregar o par RSA")
                .hasRootCauseMessage("As chaves RSA pública e privada não formam um par");
    }

    @Test
    void pemSourceRejectsWeakRsaKey() throws Exception {
        KeyPair pair = keyPair(1024);
        Path privateKey = pem("weak-private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicKey = pem("weak-public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());

        assertThatThrownBy(() -> new PemSigningKeyProvider(
                pemProperties(privateKey, publicKey, null),
                new DefaultResourceLoader()).load())
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("A chave RSA deve possuir ao menos 2048 bits");
    }

    @Test
    void configurationSelectsProviderFromEnvironmentProperty() {
        SigningKeyConfiguration configuration = new SigningKeyConfiguration();

        SigningKeyProvider generated = configuration.signingKeyProvider(
                new SigningKeyProperties(
                        SigningKeyProperties.Source.GENERATED,
                        null, null, null, null, null, null, null, null),
                new DefaultResourceLoader());
        SigningKeyProvider pem = configuration.signingKeyProvider(
                new SigningKeyProperties(
                        SigningKeyProperties.Source.PEM,
                        null, "private", "public", null, null, null, null, null),
                new DefaultResourceLoader());

        assertThat(generated).isInstanceOf(GeneratedSigningKeyProvider.class);
        assertThat(pem).isInstanceOf(PemSigningKeyProvider.class);
    }

    @Test
    void rotatingPemSelectsSigningKeyAndRetainsPreviousPublicKeyAcrossPlannedActivation() throws Exception {
        Instant activation = Instant.parse("2026-08-05T12:00:00Z");
        Duration retention = Duration.ofMinutes(10);
        SigningKeySet keys = rotatingProvider(activation, retention).load();

        assertThat(keys.signingKeyAt(activation.minusSeconds(1)).getKeyID()).isEqualTo("current-2026-08");
        assertThat(keys.jwkSetAt(activation.minusSeconds(1)).getKeys())
                .extracting(key -> key.getKeyID() + ":" + key.isPrivate())
                .containsExactly("current-2026-08:true", "next-2026-08:false");

        assertThat(keys.signingKeyAt(activation).getKeyID()).isEqualTo("next-2026-08");
        assertThat(keys.jwkSetAt(activation).getKeys())
                .extracting(key -> key.getKeyID() + ":" + key.isPrivate())
                .containsExactly("next-2026-08:true", "current-2026-08:false");

        assertThat(keys.jwkSetAt(activation.plus(retention)).getKeys())
                .extracting(key -> key.getKeyID() + ":" + key.isPrivate())
                .containsExactly("next-2026-08:true");
    }

    @Test
    void tokensRemainVerifiableDuringOverlapAndOldKeyIsRetiredAfterWindow() throws Exception {
        Instant activation = Instant.parse("2026-08-05T12:00:00Z");
        MutableClock clock = new MutableClock(activation.minusSeconds(1));
        SigningKeyConfiguration configuration = new SigningKeyConfiguration();
        var source = configuration.jwkSource(rotatingProvider(activation, Duration.ofMinutes(10)), clock);
        NimbusJwtEncoder encoder = (NimbusJwtEncoder) configuration.jwtEncoder(source);

        SignedJWT beforeRotation = token(encoder, activation);

        clock.set(activation);
        SignedJWT duringRotation = token(encoder, activation.plusSeconds(1));

        assertThat(beforeRotation.getHeader().getKeyID()).isEqualTo("current-2026-08");
        assertThat(duringRotation.getHeader().getKeyID()).isEqualTo("next-2026-08");
        assertThat(verifies(source, beforeRotation)).isTrue();
        assertThat(verifies(source, duringRotation)).isTrue();

        clock.set(activation.plus(Duration.ofMinutes(10)));
        assertThat(verifies(source, beforeRotation)).isFalse();
        assertThat(verifies(source, duringRotation)).isTrue();
    }

    @Test
    void rotatingPemFailsClosedForMissingScheduleDuplicateKidAndShortRetention() throws Exception {
        KeyPair current = keyPair(2048);
        KeyPair next = keyPair(2048);
        Path currentPrivate = pem("invalid-current-private.pem", "PRIVATE KEY", current.getPrivate().getEncoded());
        Path currentPublic = pem("invalid-current-public.pem", "PUBLIC KEY", current.getPublic().getEncoded());
        Path nextPrivate = pem("invalid-next-private.pem", "PRIVATE KEY", next.getPrivate().getEncoded());
        Path nextPublic = pem("invalid-next-public.pem", "PUBLIC KEY", next.getPublic().getEncoded());

        SigningKeyProperties missingSchedule = rotatingProperties(
                currentPrivate, currentPublic, nextPrivate, nextPublic,
                "same-kid", "next-kid", null, Duration.ofMinutes(10));
        assertThatThrownBy(() -> new RotatingPemSigningKeyProvider(
                missingSchedule, new DefaultResourceLoader()).load())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("activation-at é obrigatório");

        SigningKeyProperties duplicateKid = rotatingProperties(
                currentPrivate, currentPublic, nextPrivate, nextPublic,
                "same-kid", "same-kid", Instant.parse("2026-08-05T12:00:00Z"), Duration.ofMinutes(10));
        assertThatThrownBy(() -> new RotatingPemSigningKeyProvider(
                duplicateKid, new DefaultResourceLoader()).load())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kids distintos");

        assertThatThrownBy(() -> rotatingProperties(
                currentPrivate, currentPublic, nextPrivate, nextPublic,
                "current", "next", Instant.parse("2026-08-05T12:00:00Z"), Duration.ofMinutes(4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 5 minutos e 7 dias");
    }

    @Test
    void springConfigurationBindsPemSourceAndBuildsJwkSource() throws Exception {
        KeyPair pair = keyPair(2048);
        Path privateKey = pem("context-private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicKey = pem("context-public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());

        new ApplicationContextRunner()
                .withUserConfiguration(SigningKeyConfiguration.class)
                .withPropertyValues(
                        "identity-hub.signing-key.source=pem",
                        "identity-hub.signing-key.private-key-location=" + privateKey.toUri(),
                        "identity-hub.signing-key.public-key-location=" + publicKey.toUri())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SigningKeyProvider.class);
                    assertThat(context.getBean(SigningKeyProvider.class))
                            .isInstanceOf(PemSigningKeyProvider.class);
                    assertThat(context).hasSingleBean(com.nimbusds.jose.jwk.source.JWKSource.class);
                });
    }

    @Test
    void springConfigurationBindsRotatingPemScheduleAndBuildsEncoder() throws Exception {
        KeyPair current = keyPair(2048);
        KeyPair next = keyPair(2048);
        Path currentPrivate = pem("context-rotation-current-private.pem", "PRIVATE KEY", current.getPrivate().getEncoded());
        Path currentPublic = pem("context-rotation-current-public.pem", "PUBLIC KEY", current.getPublic().getEncoded());
        Path nextPrivate = pem("context-rotation-next-private.pem", "PRIVATE KEY", next.getPrivate().getEncoded());
        Path nextPublic = pem("context-rotation-next-public.pem", "PUBLIC KEY", next.getPublic().getEncoded());

        new ApplicationContextRunner()
                .withUserConfiguration(SigningKeyConfiguration.class)
                .withPropertyValues(
                        "identity-hub.signing-key.source=rotating-pem",
                        "identity-hub.signing-key.key-id=current-context",
                        "identity-hub.signing-key.private-key-location=" + currentPrivate.toUri(),
                        "identity-hub.signing-key.public-key-location=" + currentPublic.toUri(),
                        "identity-hub.signing-key.next-key-id=next-context",
                        "identity-hub.signing-key.next-private-key-location=" + nextPrivate.toUri(),
                        "identity-hub.signing-key.next-public-key-location=" + nextPublic.toUri(),
                        "identity-hub.signing-key.activation-at=2026-08-10T03:00:00Z",
                        "identity-hub.signing-key.previous-key-retention=15m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SigningKeyProvider.class))
                            .isInstanceOf(RotatingPemSigningKeyProvider.class);
                    assertThat(context).hasSingleBean(org.springframework.security.oauth2.jwt.JwtEncoder.class);
                });
    }

    private SigningKeyProperties pemProperties(Path privateKey, Path publicKey, String keyId) {
        return new SigningKeyProperties(
                SigningKeyProperties.Source.PEM,
                keyId,
                privateKey.toUri().toString(),
                publicKey.toUri().toString(),
                null, null, null, null, null);
    }

    private RotatingPemSigningKeyProvider rotatingProvider(Instant activation, Duration retention) throws Exception {
        KeyPair current = keyPair(2048);
        KeyPair next = keyPair(2048);
        Path currentPrivate = pem("rotation-current-private.pem", "PRIVATE KEY", current.getPrivate().getEncoded());
        Path currentPublic = pem("rotation-current-public.pem", "PUBLIC KEY", current.getPublic().getEncoded());
        Path nextPrivate = pem("rotation-next-private.pem", "PRIVATE KEY", next.getPrivate().getEncoded());
        Path nextPublic = pem("rotation-next-public.pem", "PUBLIC KEY", next.getPublic().getEncoded());
        return new RotatingPemSigningKeyProvider(
                rotatingProperties(
                        currentPrivate, currentPublic, nextPrivate, nextPublic,
                        "current-2026-08", "next-2026-08", activation, retention),
                new DefaultResourceLoader());
    }

    private SigningKeyProperties rotatingProperties(
            Path currentPrivate,
            Path currentPublic,
            Path nextPrivate,
            Path nextPublic,
            String currentKeyId,
            String nextKeyId,
            Instant activation,
            Duration retention) {
        return new SigningKeyProperties(
                SigningKeyProperties.Source.ROTATING_PEM,
                currentKeyId,
                currentPrivate.toUri().toString(),
                currentPublic.toUri().toString(),
                nextKeyId,
                nextPrivate.toUri().toString(),
                nextPublic.toUri().toString(),
                activation,
                retention);
    }

    private static SignedJWT token(NimbusJwtEncoder encoder, Instant issuedAt) throws Exception {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("rotation-test")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(30)))
                .build();
        return SignedJWT.parse(encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue());
    }

    private static boolean verifies(
            com.nimbusds.jose.jwk.source.JWKSource<com.nimbusds.jose.proc.SecurityContext> source,
            SignedJWT token) throws Exception {
        var keys = source.get(new JWKSelector(
                new JWKMatcher.Builder().keyID(token.getHeader().getKeyID()).build()), null);
        return !keys.isEmpty() && token.verify(new RSASSAVerifier(keys.get(0).toRSAKey()));
    }

    private Path pem(String name, String type, byte[] encoded) throws Exception {
        Files.createDirectories(temporaryDirectory);
        String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        String value = "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, value, StandardCharsets.US_ASCII);
        return path;
    }

    private static KeyPair keyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
