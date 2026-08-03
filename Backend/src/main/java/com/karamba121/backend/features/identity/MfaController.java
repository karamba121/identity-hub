package com.karamba121.backend.features.identity;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final MfaService mfa;

    public MfaController(MfaService mfa) {
        this.mfa = mfa;
    }

    @GetMapping
    MfaService.Status status(Principal principal) {
        return mfa.status(principal.getName());
    }

    @PostMapping("/enrollment")
    ResponseEntity<MfaService.Enrollment> enroll(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(mfa.startEnrollment(principal.getName()));
    }

    @PostMapping("/enrollment/confirm")
    ResponseEntity<RecoveryCodes> confirm(Principal principal, @RequestBody CodeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RecoveryCodes(mfa.confirmEnrollment(principal.getName(), request.code())));
    }

    @PostMapping("/recovery-codes")
    ResponseEntity<RecoveryCodes> regenerate(Principal principal, @RequestBody CodeRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RecoveryCodes(mfa.regenerateRecoveryCodes(principal.getName(), request.code())));
    }

    @DeleteMapping
    ResponseEntity<Void> disable(Principal principal, @RequestBody CodeRequest request) {
        mfa.disable(principal.getName(), request.code());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ProblemDetail> invalid(RuntimeException exception) {
        HttpStatus status = exception instanceof IllegalStateException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, exception.getMessage()));
    }

    record CodeRequest(String code) { }
    record RecoveryCodes(List<String> recoveryCodes) { }
}
