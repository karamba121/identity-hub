package com.karamba121.backend.features.identity;

import java.security.Principal;
import java.util.List;
import java.time.Instant;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.karamba121.backend.features.abuse.RateLimitService;
import com.karamba121.backend.features.abuse.RateLimitedOperation;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/mfa")
public class MfaController {

    private final MfaService mfa;
    private final IdentitySecurityAuditor auditor;
    private final RateLimitService rateLimits;

    public MfaController(MfaService mfa, IdentitySecurityAuditor auditor, RateLimitService rateLimits) {
        this.mfa = mfa;
        this.auditor = auditor;
        this.rateLimits = rateLimits;
    }

    @GetMapping
    MfaService.Status status(Principal principal) {
        return mfa.status(principal.getName());
    }

    @PostMapping("/enrollment")
    ResponseEntity<MfaService.Enrollment> enroll(Principal principal, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(auditor.execute(
                        principal.getName(),
                        com.karamba121.backend.features.access.SecurityAuditEventType.MFA_ENROLLMENT_STARTED,
                        () -> {
                            rateLimits.check(RateLimitedOperation.MFA_MANAGEMENT, request, principal.getName());
                            return mfa.startEnrollment(principal.getName());
                        }));
    }

    @PostMapping("/enrollment/confirm")
    ResponseEntity<RecoveryCodes> confirm(
            Principal principal, @RequestBody CodeRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RecoveryCodes(auditor.execute(
                        principal.getName(),
                        com.karamba121.backend.features.access.SecurityAuditEventType.MFA_ENABLED,
                        () -> {
                            rateLimits.check(RateLimitedOperation.MFA_MANAGEMENT, httpRequest, principal.getName());
                            return mfa.confirmEnrollment(principal.getName(), request.code());
                        })));
    }

    @PostMapping("/recovery-codes")
    ResponseEntity<RecoveryCodes> regenerate(
            Principal principal, @RequestBody CodeRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new RecoveryCodes(auditor.execute(
                        principal.getName(),
                        com.karamba121.backend.features.access.SecurityAuditEventType.MFA_RECOVERY_CODES_REGENERATED,
                        () -> {
                            rateLimits.check(RateLimitedOperation.MFA_MANAGEMENT, httpRequest, principal.getName());
                            return mfa.regenerateRecoveryCodes(principal.getName(), request.code());
                        })));
    }

    @DeleteMapping
    ResponseEntity<Void> disable(
            Principal principal, @RequestBody CodeRequest request, HttpServletRequest httpRequest) {
        auditor.execute(
                principal.getName(),
                com.karamba121.backend.features.access.SecurityAuditEventType.MFA_DISABLED,
                () -> {
                    rateLimits.check(RateLimitedOperation.MFA_MANAGEMENT, httpRequest, principal.getName());
                    mfa.disable(principal.getName(), request.code());
                    return null;
                });
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-events")
    AuditPage auditEvents(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = auditor.history(principal.getName(), page, size);
        return new AuditPage(
                result.getContent().stream().map(AuditEventView::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ProblemDetail> invalid(RuntimeException exception) {
        HttpStatus status = exception instanceof IllegalStateException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, exception.getMessage()));
    }

    record CodeRequest(String code) { }
    record RecoveryCodes(List<String> recoveryCodes) { }
    record AuditPage(List<AuditEventView> items, int page, int size, long totalElements, int totalPages) { }
    record AuditEventView(
            String id,
            Instant occurredAt,
            String eventType,
            String result,
            String reasonCode,
            String correlationId) {
        static AuditEventView from(com.karamba121.backend.features.access.SecurityAuditEvent event) {
            return new AuditEventView(
                    event.getId(),
                    event.getOccurredAt(),
                    event.getEventType().name(),
                    event.getResult().name(),
                    event.getReasonCode(),
                    event.getCorrelationId());
        }
    }
}
