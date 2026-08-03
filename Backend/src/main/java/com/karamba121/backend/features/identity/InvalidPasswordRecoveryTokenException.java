package com.karamba121.backend.features.identity;

public class InvalidPasswordRecoveryTokenException extends RuntimeException {

    public InvalidPasswordRecoveryTokenException() {
        super("Link de recuperação inválido ou expirado");
    }
}
