package com.karamba121.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;

class SigningKeyProviderTests {

    private final Path temporaryDirectory = Path.of(
            "target", "signing-key-tests", UUID.randomUUID().toString());

    @Test
    void generatedSourceCreatesEphemeralRsaKeysWithDistinctKeyIds() {
        GeneratedSigningKeyProvider provider = new GeneratedSigningKeyProvider();

        var first = provider.load();
        var second = provider.load();

        assertThat(first.isPrivate()).isTrue();
        assertThat(first.size()).isGreaterThanOrEqualTo(2048);
        assertThat(first.getKeyID()).isNotBlank().isNotEqualTo(second.getKeyID());
        assertThat(first.toPublicJWK()).isNotEqualTo(second.toPublicJWK());
    }

    @Test
    void pemSourceLoadsStableMatchingPairAndDerivesStableKeyId() throws Exception {
        KeyPair pair = keyPair(2048);
        Path privateKey = pem("signing-private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicKey = pem("signing-public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());
        SigningKeyProperties properties = pemProperties(privateKey, publicKey, null);

        var first = new PemSigningKeyProvider(properties, new DefaultResourceLoader()).load();
        var second = new PemSigningKeyProvider(properties, new DefaultResourceLoader()).load();

        assertThat(first.isPrivate()).isTrue();
        assertThat(first.getKeyID()).isEqualTo(first.toPublicJWK().computeThumbprint().toString());
        assertThat(second.getKeyID()).isEqualTo(first.getKeyID());
        assertThat(second.toPublicJWK()).isEqualTo(first.toPublicJWK());
    }

    @Test
    void pemSourceHonorsExplicitKeyId() throws Exception {
        KeyPair pair = keyPair(2048);
        Path privateKey = pem("explicit-private.pem", "PRIVATE KEY", pair.getPrivate().getEncoded());
        Path publicKey = pem("explicit-public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());

        var key = new PemSigningKeyProvider(
                pemProperties(privateKey, publicKey, "production-key-2026-08"),
                new DefaultResourceLoader()).load();

        assertThat(key.getKeyID()).isEqualTo("production-key-2026-08");
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
                new SigningKeyProperties(SigningKeyProperties.Source.GENERATED, null, null, null),
                new DefaultResourceLoader());
        SigningKeyProvider pem = configuration.signingKeyProvider(
                new SigningKeyProperties(SigningKeyProperties.Source.PEM, null, "private", "public"),
                new DefaultResourceLoader());

        assertThat(generated).isInstanceOf(GeneratedSigningKeyProvider.class);
        assertThat(pem).isInstanceOf(PemSigningKeyProvider.class);
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

    private SigningKeyProperties pemProperties(Path privateKey, Path publicKey, String keyId) {
        return new SigningKeyProperties(
                SigningKeyProperties.Source.PEM,
                keyId,
                privateKey.toUri().toString(),
                publicKey.toUri().toString());
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
}
