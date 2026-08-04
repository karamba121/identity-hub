package com.karamba121.backend.features.scim;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.karamba121.backend.features.access.TenantOAuthClientRepository;
import com.karamba121.backend.features.tenancy.TenantStatus;

@Service
public class ScimClientAuthorizer {

    private final TenantOAuthClientRepository clients;

    public ScimClientAuthorizer(TenantOAuthClientRepository clients) {
        this.clients = clients;
    }

    public void require(String clientId, String tenantId) {
        boolean authorized = clients.findByTenantIdAndClientId(tenantId, clientId)
                .filter(client -> client.getTenant().getStatus() == TenantStatus.ACTIVE)
                .isPresent();
        if (!authorized) {
            throw new AccessDeniedException("Cliente SCIM não pertence ao tenant solicitado");
        }
    }
}
