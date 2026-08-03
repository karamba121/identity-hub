package com.karamba121.backend.features.identity;

public class InvalidEmailVerificationTokenException extends RuntimeException {

    public InvalidEmailVerificationTokenException() {
        super("Link de verificação inválido ou expirado");
    }
}
