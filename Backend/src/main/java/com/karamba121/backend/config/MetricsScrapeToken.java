package com.karamba121.backend.config;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
final class MetricsScrapeToken {

    private static final int MAXIMUM_TOKEN_BYTES = 512;
    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9._~-]{32,512}");

    private final byte[] expected;

    MetricsScrapeToken(IdentityHubProperties properties, ResourceLoader resources) {
        IdentityHubProperties.Metrics metrics = properties.observability() == null
                ? null
                : properties.observability().metrics();
        String inline = normalize(metrics == null ? null : metrics.token());
        String location = normalize(metrics == null ? null : metrics.tokenLocation());
        if (inline != null && location != null) {
            throw new IllegalArgumentException(
                    "Configure somente uma origem para o token de métricas");
        }
        String token = inline != null ? inline : load(location, resources);
        if (token != null && !TOKEN_FORMAT.matcher(token).matches()) {
            throw new IllegalArgumentException(
                    "O token de métricas deve ter de 32 a 512 caracteres no formato seguro permitido");
        }
        this.expected = token == null ? null : token.getBytes(StandardCharsets.US_ASCII);
    }

    boolean matches(String candidate) {
        if (expected == null || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, candidate.getBytes(StandardCharsets.US_ASCII));
    }

    private static String load(String location, ResourceLoader resources) {
        if (location == null) {
            return null;
        }
        if (!location.startsWith("file:")) {
            throw new IllegalArgumentException("O token de métricas deve usar um recurso externo file:");
        }
        try {
            Resource resource = resources.getResource(location);
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("O arquivo do token de métricas não está acessível");
            }
            byte[] bytes;
            try (InputStream input = resource.getInputStream()) {
                bytes = input.readNBytes(MAXIMUM_TOKEN_BYTES + 2);
            }
            if (bytes.length > MAXIMUM_TOKEN_BYTES + 1) {
                throw new IllegalArgumentException("O token de métricas excede o limite permitido");
            }
            return normalize(new String(bytes, StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível carregar o token de métricas", exception);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
