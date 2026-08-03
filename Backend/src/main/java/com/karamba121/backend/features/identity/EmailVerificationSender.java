package com.karamba121.backend.features.identity;

public interface EmailVerificationSender {

    void send(String recipient, String displayName, String verificationUrl);
}
