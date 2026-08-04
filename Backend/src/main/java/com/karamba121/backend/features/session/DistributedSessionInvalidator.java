package com.karamba121.backend.features.session;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(FindByIndexNameSessionRepository.class)
public class DistributedSessionInvalidator {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public DistributedSessionInvalidator(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    public void invalidatePrincipal(String principalName) {
        sessions.findByPrincipalName(principalName).keySet().forEach(sessions::deleteById);
    }
}
