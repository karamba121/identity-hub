package com.karamba121.backend.features.access;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tenants/{tenantId}/memberships")
public class TenantMembershipAdministrationController {

    private final TenantMembershipAdministrationService administration;
    private final AdministrativeActionAuditor auditor;

    public TenantMembershipAdministrationController(
            TenantMembershipAdministrationService administration,
            AdministrativeActionAuditor auditor) {
        this.administration = administration;
        this.auditor = auditor;
    }

    @PutMapping("/{membershipId}/role")
    ResponseEntity<Void> assignRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String membershipId,
            @RequestBody AssignRoleRequest request) {
        return auditor.execute(
                jwt.getSubject(),
                tenantId,
                PermissionCode.TENANT_ACCESS_MANAGE,
                AdministrativeAuditEventType.TENANT_MEMBERSHIP_ROLE_ASSIGNED,
                "TENANT_MEMBERSHIP",
                membershipId,
                () -> {
                    administration.assignRole(tenantId, membershipId, required(request.roleId(), "Papel"));
                    return ResponseEntity.noContent().build();
                });
    }

    @PostMapping("/{membershipId}/suspend")
    ResponseEntity<Void> suspend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String membershipId) {
        return auditor.execute(
                jwt.getSubject(),
                tenantId,
                PermissionCode.TENANT_ACCESS_MANAGE,
                AdministrativeAuditEventType.TENANT_MEMBERSHIP_SUSPENDED,
                "TENANT_MEMBERSHIP",
                membershipId,
                () -> {
                    administration.suspend(tenantId, membershipId);
                    return ResponseEntity.noContent().build();
                });
    }

    @DeleteMapping("/{membershipId}")
    ResponseEntity<Void> remove(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String membershipId) {
        return auditor.execute(
                jwt.getSubject(),
                tenantId,
                PermissionCode.TENANT_ACCESS_MANAGE,
                AdministrativeAuditEventType.TENANT_MEMBERSHIP_REMOVED,
                "TENANT_MEMBERSHIP",
                membershipId,
                () -> {
                    administration.remove(tenantId, membershipId);
                    return ResponseEntity.noContent().build();
                });
    }

    @ExceptionHandler(LastTenantAdministratorException.class)
    ResponseEntity<ProblemDetail> lastAdministrator(LastTenantAdministratorException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(TenantAdministrationResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> resourceNotFound(TenantAdministrationResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return ResponseEntity.status(status).body(problem);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.trim();
    }

    record AssignRoleRequest(String roleId) {
    }
}
