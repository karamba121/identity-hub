package com.karamba121.backend.features.access;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum PermissionCode {
    TENANT_ACCESS_READ("tenant.access.read"),
    TENANT_ACCESS_MANAGE("tenant.access.manage"),
    OAUTH_CLIENTS_READ("oauth.clients.read"),
    OAUTH_CLIENTS_MANAGE("oauth.clients.manage"),
    SECURITY_AUDIT_READ("security.audit.read");

    private final String value;

    PermissionCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static Set<String> valuesSet() {
        return Arrays.stream(values())
                .map(PermissionCode::value)
                .collect(Collectors.toUnmodifiableSet());
    }
}
