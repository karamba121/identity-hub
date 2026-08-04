package com.karamba121.backend.features.scim;

import org.springframework.http.HttpStatus;

public final class ScimException extends RuntimeException {

    private final HttpStatus status;
    private final String scimType;

    private ScimException(HttpStatus status, String scimType, String detail) {
        super(detail);
        this.status = status;
        this.scimType = scimType;
    }

    public static ScimException notFound() {
        return new ScimException(HttpStatus.NOT_FOUND, null, "Recurso SCIM não encontrado");
    }

    public static ScimException conflict(String detail) {
        return new ScimException(HttpStatus.CONFLICT, "uniqueness", detail);
    }

    public static ScimException invalidValue(String detail) {
        return new ScimException(HttpStatus.BAD_REQUEST, "invalidValue", detail);
    }

    public static ScimException invalidFilter(String detail) {
        return new ScimException(HttpStatus.BAD_REQUEST, "invalidFilter", detail);
    }

    public static ScimException invalidSyntax(String detail) {
        return new ScimException(HttpStatus.BAD_REQUEST, "invalidSyntax", detail);
    }

    public static ScimException mutability(String detail) {
        return new ScimException(HttpStatus.BAD_REQUEST, "mutability", detail);
    }

    public static ScimException preconditionFailed() {
        return new ScimException(HttpStatus.PRECONDITION_FAILED, null, "A versão do recurso foi alterada");
    }

    public HttpStatus status() { return status; }
    public String scimType() { return scimType; }
}
