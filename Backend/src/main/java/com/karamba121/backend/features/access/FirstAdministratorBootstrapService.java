package com.karamba121.backend.features.access;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.config.IdentityHubProperties;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;

@Service
public class FirstAdministratorBootstrapService {

    private static final String LOCK_NAME = "first-administrator";
    private static final String ADMINISTRATOR_ROLE = "administrator";

    private final AdministrativeBootstrapLockRepository bootstrapLocks;
    private final IdentityUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final TenantRoleRepository roles;
    private final PermissionDefinitionRepository permissions;

    public FirstAdministratorBootstrapService(
            AdministrativeBootstrapLockRepository bootstrapLocks,
            IdentityUserRepository users,
            PasswordEncoder passwordEncoder,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            TenantRoleRepository roles,
            PermissionDefinitionRepository permissions) {
        this.bootstrapLocks = bootstrapLocks;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tenants = tenants;
        this.memberships = memberships;
        this.roles = roles;
        this.permissions = permissions;
    }

    @Transactional
    public void provision(IdentityHubProperties.Bootstrap bootstrap) {
        AdministrativeBootstrapLock bootstrapLock = bootstrapLocks.findByLockNameForUpdate(LOCK_NAME)
                .orElseThrow(() -> new IllegalStateException("Lock do bootstrap administrativo não encontrado"));

        IdentityUser user;
        Tenant tenant;
        if (bootstrapLock.isCompleted()) {
            user = users.findById(bootstrapLock.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Usuário do bootstrap administrativo não encontrado"));
            tenant = tenants.findById(bootstrapLock.getTenantId())
                    .orElseThrow(() -> new IllegalStateException("Tenant do bootstrap administrativo não encontrado"));
        } else {
            user = users.findByEmailIgnoreCase(bootstrap.userEmail()).orElseGet(() -> users.save(
                    new IdentityUser(
                            bootstrap.userEmail(),
                            bootstrap.userName(),
                            passwordEncoder.encode(bootstrap.userPassword()))));
            tenant = tenants.findBySlugIgnoreCase(bootstrap.tenantSlug())
                    .orElseGet(() -> tenants.save(new Tenant(
                            bootstrap.tenantSlug(), bootstrap.tenantName())));
        }
        TenantMembership membership = memberships.findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseGet(() -> memberships.save(new TenantMembership(tenant, user)));
        TenantRole administrator = roles.findByTenantIdAndCode(tenant.getId(), ADMINISTRATOR_ROLE)
                .orElseGet(() -> new TenantRole(tenant, ADMINISTRATOR_ROLE, "Administrador", true));
        permissions.findAllByOrderBySortOrderAsc().forEach(administrator::grant);
        administrator = roles.save(administrator);
        membership.assignRole(administrator);
        memberships.save(membership);
        if (!bootstrapLock.isCompleted()) {
            bootstrapLock.complete(tenant.getId(), user.getId());
        }
    }
}
