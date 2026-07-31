package com.karamba121.backend.features.interaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InteractionException extends ResponseStatusException {

    public InteractionException(HttpStatus status, String reason) {
        super(status, reason);
    }
}
