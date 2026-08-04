package com.karamba121.backend.features.abuse;

public class RateLimitBackendUnavailableException extends RuntimeException {

    RateLimitBackendUnavailableException(Throwable cause) {
        super("Proteção contra abuso temporariamente indisponível", cause);
    }
}
