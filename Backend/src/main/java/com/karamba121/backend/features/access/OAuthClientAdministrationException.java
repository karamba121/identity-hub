package com.karamba121.backend.features.access;

public class OAuthClientAdministrationException extends RuntimeException {

    private final boolean conflict;

    private OAuthClientAdministrationException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public static OAuthClientAdministrationException notFound() {
        return new OAuthClientAdministrationException("Cliente OAuth não encontrado no tenant", false);
    }

    public static OAuthClientAdministrationException conflict(String message) {
        return new OAuthClientAdministrationException(message, true);
    }

    public boolean isConflict() {
        return conflict;
    }
}
