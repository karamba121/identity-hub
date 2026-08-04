package com.karamba121.backend.features.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.config.IdentityHubProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@SpringBootTest(properties = {
        "identity-hub.audit-retention.enabled=true",
        "identity-hub.audit-retention.initial-delay=1d"
})
@ActiveProfiles("test")
@Transactional
class SecurityAuditRetentionIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SecurityAuditRetentionService scheduledRetention;

    @Test
    void deletesOnlyExpiredEventsUpToConfiguredBatchSize() {
        assertThat(scheduledRetention).isNotNull();
        insert("oldest", NOW.minus(Duration.ofDays(500)));
        insert("old", NOW.minus(Duration.ofDays(400)));
        insert("boundary", NOW.minus(Duration.ofDays(365)));
        insert("recent", NOW.minus(Duration.ofDays(30)));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        SecurityAuditRetentionService retention = service(policy(Duration.ofDays(365), 1), metrics);

        assertThat(retention.purgeExpiredBatch()).isEqualTo(1);
        assertThat(ids()).containsExactlyInAnyOrder("old", "boundary", "recent");
        assertThat(retention.purgeExpiredBatch()).isEqualTo(1);
        assertThat(ids()).containsExactlyInAnyOrder("boundary", "recent");
        assertThat(retention.purgeExpiredBatch()).isZero();
        assertThat(metrics.counter(SecurityAuditRetentionService.DELETED_METRIC).count()).isEqualTo(2);
    }

    @Test
    void failsClosedForUnsafeRetentionOrBatchConfiguration() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();

        assertThatThrownBy(() -> service(policy(Duration.ofDays(29), 100), metrics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 30 e 3650 dias");
        assertThatThrownBy(() -> service(policy(Duration.ofDays(365), 10_001), metrics))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entre 1 e 10000");
    }

    private SecurityAuditRetentionService service(
            IdentityHubProperties.AuditRetention policy,
            SimpleMeterRegistry metrics) {
        return new SecurityAuditRetentionService(
                policy,
                jdbc,
                new NamedParameterJdbcTemplate(jdbc),
                metrics,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private IdentityHubProperties.AuditRetention policy(Duration retention, int batchSize) {
        return new IdentityHubProperties.AuditRetention(
                true,
                retention,
                batchSize,
                Duration.ZERO,
                Duration.ofHours(1));
    }

    private void insert(String id, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO security_audit_event (
                    id, occurred_at, event_type, result, reason_code,
                    actor_id, tenant_id, target_type, target_id, correlation_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                occurredAt,
                SecurityAuditEventType.OAUTH_CLIENT_CREATED.name(),
                SecurityAuditResult.SUCCEEDED.name(),
                null,
                "retention-test-actor",
                "retention-test-tenant",
                "OAUTH_CLIENT",
                "retention-test-target",
                UUID.randomUUID().toString());
    }

    private java.util.List<String> ids() {
        return jdbc.queryForList(
                "SELECT id FROM security_audit_event WHERE actor_id = ? ORDER BY id",
                String.class,
                "retention-test-actor");
    }
}
