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
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
class TenancyContextIntegrationTests {

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

    @Test
    void requiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/demo/tenants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresDemoReadScope() throws Exception {
        mockMvc.perform(get("/api/v1/demo/tenants")
                        .header("Authorization", "Bearer " + token("user-without-scope", "openid")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsOnlyActiveMembershipsOwnedByTheAuthenticatedSubject() throws Exception {
        String suffix = UUID.randomUUID().toString();
        IdentityUser firstUser = users.save(new IdentityUser(
                "first-" + suffix + "@example.test", "Primeiro usuário", "not-used"));
        IdentityUser secondUser = users.save(new IdentityUser(
                "second-" + suffix + "@example.test", "Segundo usuário", "not-used"));
        Tenant firstTenant = tenants.save(new Tenant("first-" + suffix, "Primeiro tenant"));
        Tenant secondTenant = tenants.save(new Tenant("second-" + suffix, "Segundo tenant"));
        Tenant suspendedMembershipTenant = tenants.save(new Tenant(
                "suspended-membership-" + suffix, "Membership suspensa"));
        Tenant suspendedTenant = new Tenant("suspended-tenant-" + suffix, "Tenant suspenso");
        suspendedTenant.suspend();
        tenants.save(suspendedTenant);

        memberships.save(new TenantMembership(firstTenant, firstUser));
        memberships.save(new TenantMembership(secondTenant, secondUser));
        TenantMembership suspendedMembership = new TenantMembership(suspendedMembershipTenant, firstUser);
        suspendedMembership.suspend();
        memberships.save(suspendedMembership);
        memberships.save(new TenantMembership(suspendedTenant, firstUser));

        mockMvc.perform(get("/api/v1/demo/tenants")
                        .header("Authorization", "Bearer " + token(
                                firstUser.getId(), DemoResourceContract.SCOPE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tenantId").value(firstTenant.getId()))
                .andExpect(jsonPath("$[0].slug").value(firstTenant.getSlug()))
                .andExpect(jsonPath("$[0].displayName").value(firstTenant.getDisplayName()));
    }

    @Test
    void rejectsDuplicateMembershipForTheSameTenantAndUser() {
        String suffix = UUID.randomUUID().toString();
        IdentityUser user = users.save(new IdentityUser(
                "duplicate-" + suffix + "@example.test", "Usuário duplicado", "not-used"));
        Tenant tenant = tenants.save(new Tenant("duplicate-" + suffix, "Tenant duplicado"));
        memberships.saveAndFlush(new TenantMembership(tenant, user));

        assertThatThrownBy(() -> memberships.saveAndFlush(new TenantMembership(tenant, user)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String token(String subject, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject(subject)
                .audience(List.of(DemoResourceContract.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
