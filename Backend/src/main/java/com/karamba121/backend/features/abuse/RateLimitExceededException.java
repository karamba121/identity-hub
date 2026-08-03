package com.karamba121.backend.features.abuse;

public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    RateLimitExceededException(long retryAfterSeconds) {
        super("Muitas tentativas. Aguarde antes de tentar novamente.");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
