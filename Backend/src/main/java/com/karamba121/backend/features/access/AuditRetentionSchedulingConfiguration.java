package com.karamba121.backend.features.access;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "identity-hub.audit-retention.enabled",
        havingValue = "true")
class AuditRetentionSchedulingConfiguration {
}
