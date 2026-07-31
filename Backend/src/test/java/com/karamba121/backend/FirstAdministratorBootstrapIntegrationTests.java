package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.karamba121.backend.config.IdentityHubProperties;
import com.karamba121.backend.features.access.FirstAdministratorBootstrapService;

@SpringBootTest(properties = {
        "identity-hub.bootstrap.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:first-administrator-bootstrap;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
class FirstAdministratorBootstrapIntegrationTests {

    @Autowired
    private FirstAdministratorBootstrapService bootstrapService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void concurrentBootstrapCreatesOneCompleteAdministratorContext() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "first-admin-" + suffix + "@example.test";
        String tenantSlug = "first-admin-" + suffix;
        IdentityHubProperties.Bootstrap bootstrap = new IdentityHubProperties.Bootstrap(
                true,
                email,
                "StrongTestPassword123!",
                "Primeiro administrador",
                "unused-client-" + suffix,
                "Cliente não utilizado",
                "http://localhost:4200/unused",
                "http://localhost:4200/unused-logout",
                tenantSlug,
                "Tenant do primeiro administrador");

        int competitors = 6;
        CountDownLatch ready = new CountDownLatch(competitors);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(competitors);
        List<Future<?>> executions = new ArrayList<>();
        try {
            for (int index = 0; index < competitors; index++) {
                executions.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    bootstrapService.provision(bootstrap);
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> execution : executions) {
                execution.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(count("SELECT COUNT(*) FROM identity_user WHERE email = ?", email)).isOne();
        assertThat(count("SELECT COUNT(*) FROM tenant WHERE slug = ?", tenantSlug)).isOne();
        assertThat(count("""
                SELECT COUNT(*) FROM tenant_role role
                JOIN tenant ON tenant.id = role.tenant_id
                WHERE tenant.slug = ? AND role.code = 'administrator'
                """, tenantSlug)).isOne();
        assertThat(count("""
                SELECT COUNT(*) FROM tenant_membership membership
                JOIN tenant ON tenant.id = membership.tenant_id
                JOIN identity_user identity_user ON identity_user.id = membership.user_id
                JOIN tenant_role role ON role.id = membership.role_id
                WHERE tenant.slug = ? AND identity_user.email = ? AND role.code = 'administrator'
                """, tenantSlug, email)).isOne();
        assertThat(count("""
                SELECT COUNT(*) FROM tenant_role_permission role_permission
                JOIN tenant_role role ON role.id = role_permission.role_id
                JOIN tenant ON tenant.id = role.tenant_id
                WHERE tenant.slug = ? AND role.code = 'administrator'
                """, tenantSlug)).isEqualTo(5);

        IdentityHubProperties.Bootstrap conflictingBootstrap = new IdentityHubProperties.Bootstrap(
                true,
                "another-" + email,
                "AnotherStrongPassword123!",
                "Outro administrador",
                "another-unused-client-" + suffix,
                "Outro cliente não utilizado",
                "http://localhost:4200/another-unused",
                "http://localhost:4200/another-unused-logout",
                "another-" + tenantSlug,
                "Outro tenant");
        bootstrapService.provision(conflictingBootstrap);

        assertThat(count("SELECT COUNT(*) FROM identity_user WHERE email = ?", "another-" + email)).isZero();
        assertThat(count("SELECT COUNT(*) FROM tenant WHERE slug = ?", "another-" + tenantSlug)).isZero();
    }

    private long count(String sql, Object... arguments) {
        Long result = jdbc.queryForObject(sql, Long.class, arguments);
        return result == null ? 0 : result;
    }
}
