package com.karamba121.backend.features.scim;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.karamba121.backend.features.access.SecurityAuditEventType;
import com.karamba121.backend.features.scim.ScimUserService.PatchCommand;
import com.karamba121.backend.features.scim.ScimUserService.UserCommand;
import com.karamba121.backend.features.scim.ScimUserService.UserData;

@RestController
@RequestMapping(value = "/scim/v2/{tenantId}", produces = ScimController.SCIM_JSON)
public class ScimController {

    static final String SCIM_JSON = "application/scim+json";
    private static final MediaType SCIM_MEDIA_TYPE = MediaType.parseMediaType(SCIM_JSON);

    private final ScimUserService users;
    private final ScimClientAuthorizer authorizer;
    private final ScimAuditService auditor;

    public ScimController(
            ScimUserService users,
            ScimClientAuthorizer authorizer,
            ScimAuditService auditor) {
        this.users = users;
        this.authorizer = authorizer;
        this.auditor = auditor;
    }

    @GetMapping("/ServiceProviderConfig")
    public Map<String, Object> serviceProviderConfig(
            @PathVariable String tenantId, @AuthenticationPrincipal Jwt jwt) {
        authorizer.require(clientId(jwt), tenantId);
        return Map.of(
                "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"),
                "patch", Map.of("supported", true),
                "bulk", Map.of("supported", false, "maxOperations", 0, "maxPayloadSize", 0),
                "filter", Map.of("supported", true, "maxResults", 100),
                "changePassword", Map.of("supported", false),
                "sort", Map.of("supported", false),
                "etag", Map.of("supported", true),
                "authenticationSchemes", List.of(Map.of(
                        "type", "oauthbearertoken",
                        "name", "OAuth Bearer Token",
                        "description", "OAuth 2.0 Client Credentials com audience SCIM",
                        "specUri", "https://www.rfc-editor.org/rfc/rfc6750")));
    }

    @GetMapping("/ResourceTypes")
    public ListResponse<ResourceType> resourceTypes(
            @PathVariable String tenantId, @AuthenticationPrincipal Jwt jwt) {
        authorizer.require(clientId(jwt), tenantId);
        return new ListResponse<>(List.of(new ResourceType(
                ScimResourceContract.USER_SCHEMA,
                "User",
                "User",
                "Identidade vinculada ao tenant",
                "/Users")), 1, 1, 1);
    }

    @GetMapping("/Schemas")
    public ListResponse<SchemaDefinition> schemas(
            @PathVariable String tenantId, @AuthenticationPrincipal Jwt jwt) {
        authorizer.require(clientId(jwt), tenantId);
        return new ListResponse<>(List.of(new SchemaDefinition(
                ScimResourceContract.USER_SCHEMA,
                "User",
                "SCIM core user subset",
                List.of(
                        attribute("userName", "string", true, "immutable"),
                        attribute("displayName", "string", true, "readWrite"),
                        attribute("externalId", "string", false, "readWrite"),
                        attribute("active", "boolean", false, "readWrite")))), 1, 1, 1);
    }

    @GetMapping("/Users")
    public ListResponse<UserRepresentation> list(
            @PathVariable String tenantId,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "100") int count,
            @AuthenticationPrincipal Jwt jwt) {
        authorizer.require(clientId(jwt), tenantId);
        ScimUserService.UserPage page = users.list(tenantId, filter, startIndex, count);
        return new ListResponse<>(
                page.resources().stream().map(user -> representation(tenantId, user)).toList(),
                page.totalResults(), page.startIndex(), page.itemsPerPage());
    }

    @GetMapping("/Users/{id}")
    public ResponseEntity<UserRepresentation> get(
            @PathVariable String tenantId, @PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        authorizer.require(clientId(jwt), tenantId);
        return response(tenantId, users.get(tenantId, id), HttpStatus.OK);
    }

    @PostMapping(value = "/Users", consumes = {SCIM_JSON, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserRepresentation> create(
            @PathVariable String tenantId, @RequestBody UserCommand command, @AuthenticationPrincipal Jwt jwt) {
        UserData created = auditor.execute(
                clientId(jwt), tenantId, SecurityAuditEventType.SCIM_USER_CREATED,
                command == null ? "unknown" : String.valueOf(command.userName()),
                () -> users.create(tenantId, command));
        return response(tenantId, created, HttpStatus.CREATED);
    }

    @PutMapping(value = "/Users/{id}", consumes = {SCIM_JSON, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserRepresentation> replace(
            @PathVariable String tenantId,
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody UserCommand command,
            @AuthenticationPrincipal Jwt jwt) {
        UserData updated = auditor.execute(
                clientId(jwt), tenantId, SecurityAuditEventType.SCIM_USER_UPDATED, id,
                () -> users.replace(tenantId, id, ifMatch, command));
        return response(tenantId, updated, HttpStatus.OK);
    }

    @PatchMapping(value = "/Users/{id}", consumes = {SCIM_JSON, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<UserRepresentation> patch(
            @PathVariable String tenantId,
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @RequestBody PatchCommand command,
            @AuthenticationPrincipal Jwt jwt) {
        UserData updated = auditor.execute(
                clientId(jwt), tenantId, SecurityAuditEventType.SCIM_USER_UPDATED, id,
                () -> users.patch(tenantId, id, ifMatch, command));
        return response(tenantId, updated, HttpStatus.OK);
    }

    @DeleteMapping("/Users/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantId,
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @AuthenticationPrincipal Jwt jwt) {
        auditor.execute(
                clientId(jwt), tenantId, SecurityAuditEventType.SCIM_USER_DELETED, id,
                () -> { users.delete(tenantId, id, ifMatch); return null; });
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<UserRepresentation> response(String tenantId, UserData user, HttpStatus status) {
        URI location = location(tenantId, user.id());
        return ResponseEntity.status(status)
                .contentType(SCIM_MEDIA_TYPE)
                .location(location)
                .eTag(ScimUserService.etag(user.version()))
                .body(representation(user, location.toString()));
    }

    private UserRepresentation representation(String tenantId, UserData user) {
        return representation(user, location(tenantId, user.id()).toString());
    }

    private static UserRepresentation representation(UserData user, String location) {
        return new UserRepresentation(
                List.of(ScimResourceContract.USER_SCHEMA), user.id(), user.externalId(), user.userName(),
                user.displayName(), user.active(), new Meta(
                        "User", user.createdAt(), user.lastModifiedAt(),
                        ScimUserService.etag(user.version()), location));
    }

    private static URI location(String tenantId, String id) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .pathSegment("scim", "v2", tenantId, "Users", id).build().toUri();
    }

    private static String clientId(Jwt jwt) {
        String clientId = jwt == null ? null : jwt.getClaimAsString("client_id");
        if (clientId == null || clientId.isBlank()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Token de usuário não pode provisionar via SCIM");
        }
        return clientId;
    }

    private static Map<String, Object> attribute(
            String name, String type, boolean required, String mutability) {
        return Map.of(
                "name", name, "type", type, "multiValued", false, "required", required,
                "caseExact", false, "mutability", mutability, "returned", "default",
                "uniqueness", name.equals("userName") ? "server" : "none");
    }

    public record UserRepresentation(
            List<String> schemas,
            String id,
            @JsonInclude(JsonInclude.Include.NON_NULL) String externalId,
            String userName,
            String displayName,
            boolean active,
            Meta meta) { }
    public record Meta(String resourceType, java.time.Instant created, java.time.Instant lastModified,
            String version, String location) { }
    public record ListResponse<T>(
            List<String> schemas,
            @JsonProperty("Resources") List<T> resources,
            long totalResults,
            int startIndex,
            int itemsPerPage) {
        ListResponse(List<T> resources, long totalResults, int startIndex, int itemsPerPage) {
            this(List.of(ScimResourceContract.LIST_SCHEMA), resources, totalResults, startIndex, itemsPerPage);
        }
    }
    public record ResourceType(List<String> schemas, String id, String name, String description,
            String endpoint, String schema) {
        ResourceType(String schema, String id, String name, String description, String endpoint) {
            this(List.of("urn:ietf:params:scim:schemas:core:2.0:ResourceType"),
                    id, name, description, endpoint, schema);
        }
    }
    public record SchemaDefinition(List<String> schemas, String id, String name, String description,
            List<Map<String, Object>> attributes) {
        SchemaDefinition(String id, String name, String description, List<Map<String, Object>> attributes) {
            this(List.of("urn:ietf:params:scim:schemas:core:2.0:Schema"),
                    id, name, description, attributes);
        }
    }
}
