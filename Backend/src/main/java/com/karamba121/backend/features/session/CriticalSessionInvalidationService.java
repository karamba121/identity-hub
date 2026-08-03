package com.karamba121.backend.features.session;

import java.util.List;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CriticalSessionInvalidationService {

    private final JdbcOperations jdbcOperations;
    private final OAuth2AuthorizationService authorizations;
    private final SessionRegistry sessions;

    public CriticalSessionInvalidationService(
            JdbcOperations jdbcOperations,
            OAuth2AuthorizationService authorizations,
            SessionRegistry sessions) {
        this.jdbcOperations = jdbcOperations;
        this.authorizations = authorizations;
        this.sessions = sessions;
    }

    @Transactional
    public void invalidateForCriticalEvent(String principalName) {
        List<String> authorizationIds = jdbcOperations.queryForList(
                "select id from oauth2_authorization where principal_name = ?",
                String.class,
                principalName);
        authorizationIds.stream()
                .map(authorizations::findById)
                .filter(java.util.Objects::nonNull)
                .forEach(authorizations::remove);

        Runnable expireSessions = () -> sessions.getAllPrincipals().stream()
                .filter(principal -> principalName.equalsIgnoreCase(principalName(principal)))
                .flatMap(principal -> sessions.getAllSessions(principal, false).stream())
                .forEach(session -> session.expireNow());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    expireSessions.run();
                }
            });
        } else {
            expireSessions.run();
        }
    }

    private static String principalName(Object principal) {
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal == null ? "" : principal.toString();
    }
}
