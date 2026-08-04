package com.karamba121.backend.features.identity;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/passkeys")
public class PasskeyController {

    private final PasskeyService passkeys;

    public PasskeyController(PasskeyService passkeys) {
        this.passkeys = passkeys;
    }

    @GetMapping
    List<PasskeyService.PasskeyView> list(Principal principal) {
        return passkeys.list(principal.getName());
    }

    @GetMapping("/csrf")
    ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{credentialId}")
    ResponseEntity<Void> remove(@PathVariable String credentialId, Principal principal) {
        passkeys.remove(principal.getName(), credentialId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }
}
