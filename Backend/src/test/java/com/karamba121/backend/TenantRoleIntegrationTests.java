package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.access.PermissionCode;
import com.karamba121.backend.features.access.PermissionDefinitionRepository;
import com.karamba121.backend.features.access.TenantRole;
import com.karamba121.backend.features.access.TenantRoleRepository;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.resource.DemoResourceContract;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantRoleIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private TenantRoleRepository roles;

    @Autowired
    private PermissionDefinitionRepository permissions;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bootstrapAssignsTheTenantAdministratorWithEveryCatalogPermission() throws Exception {
        IdentityUser user = users.findByEmailIgnoreCase("admin@identityhub.local").orElseThrow();

        mockMvc.perform(get("/api/v1/demo/tenants")
                        .header("Authorization", "Bearer " + token(user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("identity-hub-demo"))
                .andExpect(jsonPath("$[0].role.code").value("administrator"))
                .andExpect(jsonPath("$[0].role.displayName").value("Administrador"))
                .andExpect(jsonPath("$[0].role.permissions.length()").value(PermissionCode.values().length));
    }

    @Test
    void resolvesOnlyTheRoleAndPermissionsFromTheAuthenticatedUsersTenant() throws Exception {
        String suffix = UUID.randomUUID().toString();
        IdentityUser firstUser = users.save(new IdentityUser(
                "role-first-" + suffix + "@example.test", "Primeiro papel", "not-used"));
        IdentityUser secondUser = users.save(new IdentityUser(
                "role-second-" + suffix + "@example.test", "Segundo papel", "not-used"));
        Tenant firstTenant = tenants.save(new Tenant("role-first-" + suffix, "Primeiro tenant"));
        Tenant secondTenant = tenants.save(new Tenant("role-second-" + suffix, "Segundo tenant"));

        TenantRole firstRole = new TenantRole(firstTenant, "administrator", "Administrador A", false);
        firstRole.grant(permissions.findById(PermissionCode.TENANT_ACCESS_READ.value()).orElseThrow());
        roles.save(firstRole);
        TenantRole secondRole = new TenantRole(secondTenant, "administrator", "Administrador B", false);
        secondRole.grant(permissions.findById(PermissionCode.SECURITY_AUDIT_READ.value()).orElseThrow());
        roles.save(secondRole);

        TenantMembership firstMembership = new TenantMembership(firstTenant, firstUser);
        firstMembership.assignRole(firstRole);
        memberships.save(firstMembership);
        TenantMembership secondMembership = new TenantMembership(secondTenant, secondUser);
        secondMembership.assignRole(secondRole);
        memberships.save(secondMembership);

        mockMvc.perform(get("/api/v1/demo/tenants")
                        .header("Authorization", "Bearer " + token(firstUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tenantId").value(firstTenant.getId()))
                .andExpect(jsonPath("$[0].role.displayName").value("Administrador A"))
                .andExpect(jsonPath("$[0].role.permissions.length()").value(1))
                .andExpect(jsonPath("$[0].role.permissions[0]")
                        .value(PermissionCode.TENANT_ACCESS_READ.value()));
    }

    @Test
    void rejectsCrossTenantRoleAssignmentInTheDomainAndDatabase() {
        String suffix = UUID.randomUUID().toString();
        IdentityUser user = users.save(new IdentityUser(
                "cross-role-" + suffix + "@example.test", "Papel cruzado", "not-used"));
        Tenant membershipTenant = tenants.save(new Tenant(
                "membership-" + suffix, "Tenant da membership"));
        Tenant roleTenant = tenants.save(new Tenant("role-" + suffix, "Tenant do papel"));
        TenantRole foreignRole = roles.saveAndFlush(new TenantRole(
                roleTenant, "administrator", "Administrador externo", false));
        TenantMembership membership = memberships.saveAndFlush(new TenantMembership(membershipTenant, user));

        assertThatThrownBy(() -> membership.assignRole(foreignRole))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mesmo tenant");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE tenant_membership SET role_id = ? WHERE id = ?",
                foreignRole.getId(), membership.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleCodeIsUniqueInsideEachTenant() {
        String suffix = UUID.randomUUID().toString();
        Tenant tenant = tenants.save(new Tenant("unique-role-" + suffix, "Tenant com papel único"));
        roles.saveAndFlush(new TenantRole(tenant, "administrator", "Primeiro administrador", false));

        assertThatThrownBy(() -> roles.saveAndFlush(new TenantRole(
                tenant, "administrator", "Segundo administrador", false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String token(String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject(subject)
                .claim("credential_version", "0")
                .audience(List.of(DemoResourceContract.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", DemoResourceContract.SCOPE)
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
