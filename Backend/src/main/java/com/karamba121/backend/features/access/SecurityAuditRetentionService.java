package com.karamba121.backend.features.access;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.config.IdentityHubProperties;

import io.micrometer.core.instrument.MeterRegistry;

@Service
@ConditionalOnProperty(
        name = "identity-hub.audit-retention.enabled",
        havingValue = "true")
public class SecurityAuditRetentionService {

    static final String DELETED_METRIC = "identity_hub.audit.retention.events.deleted";
    static final String FAILURE_METRIC = "identity_hub.audit.retention.failures";

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAuditRetentionService.class);
    private static final Duration MINIMUM_RETENTION = Duration.ofDays(30);
    private static final Duration MAXIMUM_RETENTION = Duration.ofDays(3650);
    private static final int MAXIMUM_BATCH_SIZE = 10_000;

    private final IdentityHubProperties.AuditRetention policy;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final MeterRegistry metrics;
    private final Clock clock;

    @Autowired
    public SecurityAuditRetentionService(
            IdentityHubProperties properties,
            JdbcTemplate jdbc,
            NamedParameterJdbcTemplate namedJdbc,
            MeterRegistry metrics) {
        this(properties.auditRetention(), jdbc, namedJdbc, metrics, Clock.systemUTC());
    }

    SecurityAuditRetentionService(
            IdentityHubProperties.AuditRetention policy,
            JdbcTemplate jdbc,
            NamedParameterJdbcTemplate namedJdbc,
            MeterRegistry metrics,
            Clock clock) {
        validate(policy);
        this.policy = policy;
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${identity-hub.audit-retention.initial-delay:1m}",
            fixedDelayString = "${identity-hub.audit-retention.interval:1h}")
    @Transactional
    public void purgeExpiredAuditEvents() {
        try {
            int deleted = purgeExpiredBatch();
            if (deleted > 0) {
                LOGGER.info("Retenção de auditoria removeu {} eventos expirados", deleted);
            }
        } catch (RuntimeException exception) {
            metrics.counter(FAILURE_METRIC).increment();
            throw exception;
        }
    }

    int purgeExpiredBatch() {
        Instant cutoff = clock.instant().minus(policy.retention());
        List<String> ids = jdbc.queryForList("""
                SELECT id
                  FROM security_audit_event
                 WHERE occurred_at < ?
                 ORDER BY occurred_at ASC, id ASC
                 LIMIT ?
                """, String.class, Timestamp.from(cutoff), policy.batchSize());
        if (ids.isEmpty()) {
            return 0;
        }
        int deleted = namedJdbc.update("""
                DELETE FROM security_audit_event
                 WHERE occurred_at < :cutoff
                   AND id IN (:ids)
                """, new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("ids", ids));
        metrics.counter(DELETED_METRIC).increment(deleted);
        return deleted;
    }

    private static void validate(IdentityHubProperties.AuditRetention policy) {
        if (policy == null || policy.retention() == null
                || policy.retention().compareTo(MINIMUM_RETENTION) < 0
                || policy.retention().compareTo(MAXIMUM_RETENTION) > 0) {
            throw new IllegalArgumentException(
                    "identity-hub.audit-retention.retention deve estar entre 30 e 3650 dias");
        }
        if (policy.batchSize() < 1 || policy.batchSize() > MAXIMUM_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "identity-hub.audit-retention.batch-size deve estar entre 1 e 10000");
        }
        if (policy.initialDelay() == null || policy.initialDelay().isNegative()
                || policy.interval() == null || policy.interval().isZero() || policy.interval().isNegative()) {
            throw new IllegalArgumentException(
                    "identity-hub.audit-retention requer atrasos não negativos e intervalo positivo");
        }
    }
}
