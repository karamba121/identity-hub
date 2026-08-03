package com.karamba121.backend.features.identity;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karamba121.backend.features.abuse.RateLimitService;
import com.karamba121.backend.features.abuse.RateLimitedOperation;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/password-recovery")
public class PasswordRecoveryController {

    private static final RecoveryAccepted ACCEPTED = new RecoveryAccepted(
            "Se houver uma conta elegível, enviaremos um link de recuperação para o e-mail informado.");

    private final PasswordRecoveryService recovery;
    private final RateLimitService rateLimits;

    public PasswordRecoveryController(PasswordRecoveryService recovery, RateLimitService rateLimits) {
        this.recovery = recovery;
        this.rateLimits = rateLimits;
    }

    @PostMapping
    ResponseEntity<RecoveryAccepted> request(
            @RequestBody RecoveryRequest request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            throw new IllegalArgumentException("Dados da recuperação são obrigatórios");
        }
        rateLimits.check(RateLimitedOperation.PASSWORD_RECOVERY_REQUEST, httpRequest, request.email());
        recovery.request(request.email());
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/complete")
    ResponseEntity<Void> complete(
            @RequestBody CompleteRecoveryRequest request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            throw new InvalidPasswordRecoveryTokenException();
        }
        rateLimits.check(RateLimitedOperation.PASSWORD_RECOVERY_COMPLETE, httpRequest, request.token());
        recovery.complete(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException exception) {
        return problem(exception.getMessage());
    }

    @ExceptionHandler(InvalidPasswordRecoveryTokenException.class)
    ResponseEntity<ProblemDetail> invalidToken(InvalidPasswordRecoveryTokenException exception) {
        return problem(exception.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(String detail) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail));
    }

    record RecoveryRequest(String email) {
    }

    record CompleteRecoveryRequest(String token, String newPassword) {
    }

    record RecoveryAccepted(String message) {
    }

}
