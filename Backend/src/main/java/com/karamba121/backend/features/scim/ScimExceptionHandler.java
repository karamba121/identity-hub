package com.karamba121.backend.features.scim;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fasterxml.jackson.annotation.JsonInclude;

@RestControllerAdvice(assignableTypes = ScimController.class)
public class ScimExceptionHandler {

    @ExceptionHandler(ScimException.class)
    ResponseEntity<ScimError> scim(ScimException exception) {
        return response(exception.status(), exception.scimType(), exception.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ScimError> denied(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, null, "O cliente não pode acessar este tenant");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ScimError> malformed(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "invalidSyntax", "Documento SCIM inválido");
    }

    private static ResponseEntity<ScimError> response(HttpStatus status, String scimType, String detail) {
        return ResponseEntity.status(status)
                .contentType(org.springframework.http.MediaType.parseMediaType(ScimController.SCIM_JSON))
                .body(new ScimError(
                        List.of(ScimResourceContract.ERROR_SCHEMA), scimType,
                        Integer.toString(status.value()), detail));
    }

    record ScimError(
            List<String> schemas,
            @JsonInclude(JsonInclude.Include.NON_NULL) String scimType,
            String status,
            String detail) { }
}
