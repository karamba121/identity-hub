package com.karamba121.backend.features.identity;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/password-recovery")
public class PasswordRecoveryController {

    private static final RecoveryAccepted ACCEPTED = new RecoveryAccepted(
            "Se houver uma conta elegível, enviaremos um link de recuperação para o e-mail informado.");

    private final PasswordRecoveryService recovery;

    public PasswordRecoveryController(PasswordRecoveryService recovery) {
        this.recovery = recovery;
    }

    @PostMapping
    ResponseEntity<RecoveryAccepted> request(@RequestBody RecoveryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dados da recuperação são obrigatórios");
        }
        recovery.request(request.email());
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/complete")
    ResponseEntity<Void> complete(@RequestBody CompleteRecoveryRequest request) {
        if (request == null) {
            throw new InvalidPasswordRecoveryTokenException();
        }
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
