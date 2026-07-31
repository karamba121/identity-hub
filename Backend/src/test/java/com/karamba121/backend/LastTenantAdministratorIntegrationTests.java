package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.karamba121.backend.features.access.LastTenantAdministratorException;
import com.karamba121.backend.features.access.TenantMembershipAdministrationService;
import com.karamba121.backend.features.access.TenantRole;
import com.karamba121.backend.features.access.TenantRoleRepository;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.tenancy.MembershipStatus;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;

@SpringBootTest(properties = {
        "identity-hub.bootstrap.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:last-tenant-administrator;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class LastTenantAdministratorIntegrationTests {

    @Autowired
    private TenantMembershipAdministrationService administration;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TenantRoleRepository roles;

    @Autowired
    private TenantMembershipRepository memberships;

    @Test
    void blocksDemotionSuspensionAndRemovalOfTheLastValidAdministrator() {
        Tenant tenant = tenant("protected");
        TenantRole administrator = role(tenant, "administrator", "Administrador");
        TenantRole operator = role(tenant, "operator", "Operador");
        TenantMembership membership = membership(tenant, administrator, "protected");

        assertThatThrownBy(() -> administration.assignRole(
                tenant.getId(), membership.getId(), operator.getId()))
                .isInstanceOf(LastTenantAdministratorException.class);
        assertThatThrownBy(() -> administration.suspend(tenant.getId(), membership.getId()))
                .isInstanceOf(LastTenantAdministratorException.class);
        assertThatThrownBy(() -> administration.remove(tenant.getId(), membership.getId()))
                .isInstanceOf(LastTenantAdministratorException.class);

        TenantMembership protectedMembership = memberships
                .findByIdAndTenantId(membership.getId(), tenant.getId())
                .orElseThrow();
        assertThat(protectedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(protectedMembership.getRole().getCode()).isEqualTo("administrator");
    }

    @Test
    void concurrentRemovalsLeaveOneValidAdministrator() throws Exception {
        Tenant tenant = tenant("concurrent");
        TenantRole administrator = role(tenant, "administrator", "Administrador");
        TenantMembership first = membership(tenant, administrator, "first");
        TenantMembership second = membership(tenant, administrator, "second");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> executions = new ArrayList<>();
        try {
            for (String membershipId : List.of(first.getId(), second.getId())) {
                executions.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        administration.remove(tenant.getId(), membershipId);
                        return "removed";
                    } catch (LastTenantAdministratorException exception) {
                        return "protected";
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> execution : executions) {
                results.add(execution.get());
            }
            assertThat(results).containsExactlyInAnyOrder("removed", "protected");
        } finally {
            executor.shutdownNow();
        }

        assertThat(memberships.countValidAdministrators(
                tenant.getId(), MembershipStatus.ACTIVE, "administrator")).isOne();
    }

    private Tenant tenant(String label) {
        String suffix = UUID.randomUUID().toString();
        return tenants.save(new Tenant(label + "-" + suffix, "Tenant " + label));
    }

    private TenantRole role(Tenant tenant, String code, String displayName) {
        return roles.save(new TenantRole(tenant, code, displayName, false));
    }

    private TenantMembership membership(Tenant tenant, TenantRole role, String label) {
        String suffix = UUID.randomUUID().toString();
        IdentityUser user = users.save(new IdentityUser(
                label + "-" + suffix + "@example.test", "Usuário " + label, "not-used"));
        TenantMembership membership = new TenantMembership(tenant, user);
        membership.assignRole(role);
        return memberships.save(membership);
    }
}
