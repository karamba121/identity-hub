package com.karamba121.backend.features.identity;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karamba121.backend.features.abuse.RateLimitService;
import com.karamba121.backend.features.abuse.RateLimitedOperation;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/registrations")
public class RegistrationController {

    private static final RegistrationAccepted ACCEPTED = new RegistrationAccepted(
            "Se o cadastro puder ser criado, enviaremos um link de verificação para o e-mail informado.");

    private final RegistrationService registrations;
    private final RateLimitService rateLimits;

    public RegistrationController(RegistrationService registrations, RateLimitService rateLimits) {
        this.registrations = registrations;
        this.rateLimits = rateLimits;
    }

    @GetMapping("/csrf")
    CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    @PostMapping
    ResponseEntity<RegistrationAccepted> register(
            @RequestBody RegistrationRequest request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            throw new IllegalArgumentException("Dados do cadastro são obrigatórios");
        }
        rateLimits.check(RateLimitedOperation.REGISTRATION, httpRequest, request.email());
        registrations.register(request.email(), request.displayName(), request.password());
        return ResponseEntity.accepted().body(ACCEPTED);
    }

    @PostMapping("/verify")
    ResponseEntity<Void> verify(
            @RequestBody VerificationRequest request,
            HttpServletRequest httpRequest) {
        if (request == null) {
            throw new InvalidEmailVerificationTokenException();
        }
        rateLimits.check(RateLimitedOperation.EMAIL_VERIFICATION, httpRequest, request.token());
        registrations.verify(request.token());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(InvalidEmailVerificationTokenException.class)
    ResponseEntity<ProblemDetail> invalidToken(InvalidEmailVerificationTokenException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }

    record RegistrationRequest(String email, String displayName, String password) {
    }

    record VerificationRequest(String token) {
    }

    record RegistrationAccepted(String message) {
    }

    record CsrfView(String headerName, String token) {
    }
}
