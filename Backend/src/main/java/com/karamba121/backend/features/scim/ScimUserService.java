package com.karamba121.backend.features.scim;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.tenancy.MembershipStatus;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;
import com.karamba121.backend.features.tenancy.TenantStatus;

@Service
public class ScimUserService {

    private static final Pattern FILTER = Pattern.compile(
            "^(userName|externalId)\\s+eq\\s+\"([^\"]+)\"$", Pattern.CASE_INSENSITIVE);

    private final ScimUserResourceRepository resources;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final IdentityUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    public ScimUserService(
            ScimUserResourceRepository resources,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            IdentityUserRepository users,
            PasswordEncoder passwordEncoder,
            EntityManager entityManager) {
        this.resources = resources;
        this.tenants = tenants;
        this.memberships = memberships;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
    }

    @Transactional
    public UserData create(String tenantId, UserCommand command) {
        UserCommand validated = validate(command);
        Tenant tenant = activeTenant(tenantId);
        ScimUserResource existing = resources
                .findByTenantIdAndUserNameIgnoreCase(tenantId, validated.userName())
                .orElse(null);
        if (existing != null) {
            if (existing.getDeletedAt() == null) {
                throw ScimException.conflict("userName já existe neste tenant");
            }
            existing.restore(validated.displayName(), validated.externalId(), validated.active());
            return data(save(existing));
        }

        IdentityUser user = users.findByEmailIgnoreCase(validated.userName())
                .orElseGet(() -> users.save(IdentityUser.provisioned(
                        validated.userName(),
                        validated.displayName(),
                        passwordEncoder.encode(UUID.randomUUID().toString()))));
        if (memberships.findByTenantIdAndUserId(tenantId, user.getId()).isPresent()) {
            throw ScimException.conflict(
                    "A membership existente não pode ser assumida pelo provisionador SCIM");
        }
        TenantMembership membership = memberships.save(new TenantMembership(tenant, user));
        if (validated.active()) {
            membership.activate();
        } else {
            membership.suspend();
        }
        return data(save(new ScimUserResource(
                tenant,
                membership,
                validated.userName(),
                validated.displayName(),
                validated.externalId())));
    }

    @Transactional(readOnly = true)
    public UserData get(String tenantId, String id) {
        return data(resource(tenantId, id));
    }

    @Transactional(readOnly = true)
    public UserPage list(String tenantId, String filter, int startIndex, int count) {
        activeTenant(tenantId);
        int safeStart = Math.max(1, startIndex);
        int safeCount = Math.min(Math.max(0, count), 100);
        if (filter != null && !filter.isBlank()) {
            Matcher matcher = FILTER.matcher(filter.trim());
            if (!matcher.matches()) {
                throw ScimException.invalidFilter(
                        "Somente filtros exatos por userName ou externalId são suportados");
            }
            ScimUserResource match = matcher.group(1).equalsIgnoreCase("userName")
                    ? resources.findByTenantIdAndUserNameIgnoreCaseAndDeletedAtIsNull(tenantId, matcher.group(2))
                            .orElse(null)
                    : resources.findByTenantIdAndExternalIdAndDeletedAtIsNull(tenantId, matcher.group(2))
                            .orElse(null);
            List<UserData> values = match == null || safeCount == 0 || safeStart > 1
                    ? List.of()
                    : List.of(data(match));
            return new UserPage(values, match == null ? 0 : 1, safeStart, values.size());
        }
        if (safeCount == 0) {
            long total = count(tenantId);
            return new UserPage(List.of(), total, safeStart, 0);
        }
        List<UserData> values = entityManager.createQuery("""
                select resource from ScimUserResource resource
                join fetch resource.tenant
                join fetch resource.membership membership
                join fetch membership.user
                where resource.tenant.id = :tenantId and resource.deletedAt is null
                order by resource.userName asc
                """, ScimUserResource.class)
                .setParameter("tenantId", tenantId)
                .setFirstResult(safeStart - 1)
                .setMaxResults(safeCount)
                .getResultList().stream().map(this::data).toList();
        return new UserPage(values, count(tenantId), safeStart, values.size());
    }

    @Transactional
    public UserData replace(String tenantId, String id, String ifMatch, UserCommand command) {
        ScimUserResource resource = resource(tenantId, id);
        requireVersion(resource, ifMatch);
        UserCommand validated = validate(command);
        resource.replace(
                validated.userName(), validated.displayName(), validated.externalId(), validated.active());
        return data(save(resource));
    }

    @Transactional
    public UserData patch(String tenantId, String id, String ifMatch, PatchCommand command) {
        ScimUserResource resource = resource(tenantId, id);
        requireVersion(resource, ifMatch);
        if (command == null || command.schemas() == null
                || !command.schemas().contains(ScimResourceContract.PATCH_SCHEMA)
                || command.operations() == null || command.operations().isEmpty()) {
            throw ScimException.invalidSyntax("PatchOp e ao menos uma operação são obrigatórios");
        }
        for (PatchOperation operation : command.operations()) {
            apply(resource, operation);
        }
        resource.markModified();
        return data(save(resource));
    }

    @Transactional
    public void delete(String tenantId, String id, String ifMatch) {
        ScimUserResource resource = resource(tenantId, id);
        requireVersion(resource, ifMatch);
        resource.delete();
        save(resource);
    }

    private void apply(ScimUserResource resource, PatchOperation operation) {
        if (operation == null || operation.op() == null || operation.path() == null) {
            throw ScimException.invalidSyntax("Operação, path e value são obrigatórios");
        }
        String op = operation.op().trim().toLowerCase(Locale.ROOT);
        String path = operation.path().trim().toLowerCase(Locale.ROOT);
        Object value = operation.value();
        if (!(op.equals("add") || op.equals("replace") || op.equals("remove"))) {
            throw ScimException.invalidSyntax("Operação PATCH não suportada: " + operation.op());
        }
        switch (path) {
            case "displayname" -> {
                if (op.equals("remove") || !(value instanceof String text)) {
                    throw ScimException.invalidValue("displayName não pode ser removido");
                }
                resource.patchDisplayName(text);
            }
            case "externalid" -> resource.patchExternalId(
                    op.equals("remove") || value == null ? null : text(value, "externalId"));
            case "active" -> {
                if (op.equals("remove") || !(value instanceof Boolean active)) {
                    throw ScimException.invalidValue("active exige valor booleano");
                }
                resource.patchActive(active);
            }
            case "username" -> throw ScimException.mutability("userName é imutável neste provedor");
            default -> throw ScimException.invalidValue("Atributo PATCH não suportado: " + operation.path());
        }
    }

    private static String text(Object value, String field) {
        if (!(value instanceof String text)) {
            throw ScimException.invalidValue(field + " exige texto");
        }
        return text;
    }

    private ScimUserResource save(ScimUserResource resource) {
        try {
            return resources.saveAndFlush(resource);
        } catch (DataIntegrityViolationException exception) {
            throw ScimException.conflict("userName ou externalId já existe neste tenant");
        }
    }

    private ScimUserResource resource(String tenantId, String id) {
        return resources.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(ScimException::notFound);
    }

    private Tenant activeTenant(String tenantId) {
        return tenants.findById(tenantId)
                .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(ScimException::notFound);
    }

    private long count(String tenantId) {
        return entityManager.createQuery("""
                select count(resource) from ScimUserResource resource
                where resource.tenant.id = :tenantId and resource.deletedAt is null
                """, Long.class).setParameter("tenantId", tenantId).getSingleResult();
    }

    private static UserCommand validate(UserCommand command) {
        if (command == null || command.schemas() == null
                || !command.schemas().contains(ScimResourceContract.USER_SCHEMA)) {
            throw ScimException.invalidSyntax("O schema SCIM User é obrigatório");
        }
        String userName = required(command.userName(), "userName");
        String displayName = required(command.displayName(), "displayName");
        return new UserCommand(
                command.schemas(), userName, displayName, command.externalId(),
                command.active() == null || command.active());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw ScimException.invalidValue(field + " é obrigatório");
        }
        return value.trim();
    }

    private static void requireVersion(ScimUserResource resource, String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return;
        }
        if (!etag(resource.getVersion()).equals(ifMatch.trim())) {
            throw ScimException.preconditionFailed();
        }
    }

    public static String etag(long version) {
        return "W/\"" + version + "\"";
    }

    private UserData data(ScimUserResource resource) {
        return new UserData(
                resource.getId(), resource.getUserName(), resource.getDisplayName(), resource.getExternalId(),
                resource.getMembership().getStatus() == MembershipStatus.ACTIVE,
                resource.getVersion(), resource.getCreatedAt(), resource.getLastModifiedAt());
    }

    public record UserCommand(
            List<String> schemas, String userName, String displayName, String externalId, Boolean active) { }
    public record PatchCommand(
            List<String> schemas,
            @com.fasterxml.jackson.annotation.JsonProperty("Operations") List<PatchOperation> operations) { }
    public record PatchOperation(String op, String path, Object value) { }
    public record UserData(
            String id, String userName, String displayName, String externalId, boolean active,
            long version, java.time.Instant createdAt, java.time.Instant lastModifiedAt) { }
    public record UserPage(List<UserData> resources, long totalResults, int startIndex, int itemsPerPage) { }
}
