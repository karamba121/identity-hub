package com.karamba121.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class MetricsScrapeTokenTests {

    private static final String VALID_TOKEN = "metrics-token-0123456789abcdefghi";
    private final Path temporaryDirectory = Path.of(
            "target", "metrics-token-tests", UUID.randomUUID().toString());

    @Test
    void loadsExternalTokenAndComparesWithoutAcceptingDifferentValue() throws Exception {
        Files.createDirectories(temporaryDirectory);
        Path tokenFile = temporaryDirectory.resolve("metrics-token");
        Files.writeString(tokenFile, VALID_TOKEN + System.lineSeparator());

        MetricsScrapeToken token = token(null, tokenFile.toUri().toString());

        assertThat(token.matches(VALID_TOKEN)).isTrue();
        assertThat(token.matches("metrics-token-0123456789abcdefghj")).isFalse();
        assertThat(token.matches(null)).isFalse();
    }

    @Test
    void failsClosedForAmbiguousWeakOrEmbeddedResourceConfiguration() {
        assertThatThrownBy(() -> token(VALID_TOKEN, "file:/run/secrets/metrics"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("somente uma origem");
        assertThatThrownBy(() -> token("short-token", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 a 512");
        assertThatThrownBy(() -> token(null, "classpath:metrics-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recurso externo file:");
    }

    private MetricsScrapeToken token(String inline, String location) {
        IdentityHubProperties properties = new IdentityHubProperties(
                null, null, null, null, null, null, null, null, null,
                new IdentityHubProperties.Observability(
                        new IdentityHubProperties.Metrics(inline, location)),
                null,
                null);
        return new MetricsScrapeToken(properties, new DefaultResourceLoader());
    }
}
