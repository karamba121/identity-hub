package com.karamba121.backend.features.access;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.karamba121.backend.features.access.OAuthClientAdministrationService.OAuthClientCommand;
import com.karamba121.backend.features.access.OAuthClientAdministrationService.OAuthClientView;

@RestController
@RequestMapping("/api/v1/admin/tenants/{tenantId}/oauth-clients")
public class OAuthClientAdministrationController {

    private final TenantPermissionAuthorizer authorizer;
    private final OAuthClientAdministrationService administration;
    private final AdministrativeActionAuditor auditor;

    public OAuthClientAdministrationController(
            TenantPermissionAuthorizer authorizer,
            OAuthClientAdministrationService administration,
            AdministrativeActionAuditor auditor) {
        this.authorizer = authorizer;
        this.administration = administration;
        this.auditor = auditor;
    }

    @GetMapping
    List<OAuthClientView> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId) {
        authorizer.require(jwt.getSubject(), tenantId, PermissionCode.OAUTH_CLIENTS_READ);
        return administration.list(tenantId);
    }

    @GetMapping("/{clientId}")
    OAuthClientView get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String clientId) {
        authorizer.require(jwt.getSubject(), tenantId, PermissionCode.OAUTH_CLIENTS_READ);
        return administration.get(tenantId, clientId);
    }

    @PostMapping
    ResponseEntity<OAuthClientView> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestBody CreateOAuthClientRequest request) {
        return auditor.execute(
                jwt.getSubject(),
                tenantId,
                PermissionCode.OAUTH_CLIENTS_MANAGE,
                AdministrativeAuditEventType.OAUTH_CLIENT_CREATED,
                "OAUTH_CLIENT",
                request.clientId(),
                () -> {
                    OAuthClientView created = administration.create(tenantId, request.command());
                    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                            .pathSegment(created.clientId())
                            .build()
                            .toUri();
                    return ResponseEntity.created(location).body(created);
                });
    }

    @PutMapping("/{clientId}")
    OAuthClientView update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String clientId,
            @RequestBody UpdateOAuthClientRequest request) {
        return auditor.execute(
                jwt.getSubject(),
                tenantId,
                PermissionCode.OAUTH_CLIENTS_MANAGE,
                AdministrativeAuditEventType.OAUTH_CLIENT_UPDATED,
                "OAUTH_CLIENT",
                clientId,
                () -> administration.update(tenantId, clientId, request.command()));
    }

    @DeleteMapping("/{clientId}")
    ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @PathVariable String clientId) {
        return auditor.execute(
                jwt.getSubject(),
                tenantId,
                PermissionCode.OAUTH_CLIENTS_MANAGE,
                AdministrativeAuditEventType.OAUTH_CLIENT_DELETED,
                "OAUTH_CLIENT",
                clientId,
                () -> {
                    administration.delete(tenantId, clientId);
                    return ResponseEntity.noContent().build();
                });
    }

    @ExceptionHandler(OAuthClientAdministrationException.class)
    ResponseEntity<ProblemDetail> administrationError(OAuthClientAdministrationException exception) {
        HttpStatus status = exception.isConflict() ? HttpStatus.CONFLICT : HttpStatus.NOT_FOUND;
        return problem(status, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }

    record CreateOAuthClientRequest(
            String clientId,
            String clientName,
            Set<String> redirectUris,
            Set<String> postLogoutRedirectUris,
            Set<String> scopes) {

        OAuthClientCommand command() {
            return new OAuthClientCommand(
                    clientId, clientName, redirectUris, postLogoutRedirectUris, scopes);
        }
    }

    record UpdateOAuthClientRequest(
            String clientName,
            Set<String> redirectUris,
            Set<String> postLogoutRedirectUris,
            Set<String> scopes) {

        OAuthClientCommand command() {
            return new OAuthClientCommand(
                    null, clientName, redirectUris, postLogoutRedirectUris, scopes);
        }
    }
}
