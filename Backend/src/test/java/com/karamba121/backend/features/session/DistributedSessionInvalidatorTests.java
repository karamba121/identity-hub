package com.karamba121.backend.features.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

class DistributedSessionInvalidatorTests {

    @Test
    @SuppressWarnings("unchecked")
    void deletesEveryIndexedSessionForThePrincipal() {
        FindByIndexNameSessionRepository<Session> sessions = mock(FindByIndexNameSessionRepository.class);
        Session first = mock(Session.class);
        Session second = mock(Session.class);
        when(sessions.findByPrincipalName("person@example.test"))
                .thenReturn(Map.of("session-a", first, "session-b", second));

        new DistributedSessionInvalidator(sessions).invalidatePrincipal("person@example.test");

        verify(sessions).deleteById("session-a");
        verify(sessions).deleteById("session-b");
    }
}
