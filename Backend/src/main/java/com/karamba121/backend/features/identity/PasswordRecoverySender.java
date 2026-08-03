package com.karamba121.backend.features.identity;

public interface PasswordRecoverySender {

    void send(String recipient, String displayName, String recoveryUrl);
}
