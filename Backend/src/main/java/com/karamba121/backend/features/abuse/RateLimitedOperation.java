package com.karamba121.backend.features.abuse;

public enum RateLimitedOperation {
    LOGIN("login"),
    REGISTRATION("registration"),
    EMAIL_VERIFICATION("email_verification"),
    PASSWORD_RECOVERY_REQUEST("password_recovery_request"),
    PASSWORD_RECOVERY_COMPLETE("password_recovery_complete");

    private final String metricTag;

    RateLimitedOperation(String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}
