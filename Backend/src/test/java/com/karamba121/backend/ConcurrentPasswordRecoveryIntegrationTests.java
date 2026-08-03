package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.identity.InvalidPasswordRecoveryTokenException;
import com.karamba121.backend.features.identity.PasswordRecoverySender;
import com.karamba121.backend.features.identity.PasswordRecoveryService;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentPasswordRecoveryIntegrationTests {

    private static final String FIRST_PASSWORD = "First concurrent recovery phrase 2026";
    private static final String SECOND_PASSWORD = "Second concurrent recovery phrase 2026";

    @Autowired IdentityUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PasswordRecoveryService recovery;
    @Autowired JdbcOperations jdbc;

    @MockitoBean PasswordRecoverySender sender;

    @Test
    void concurrentRecoveryConsumesTheTokenExactlyOnce() throws Exception {
        IdentityUser user = users.saveAndFlush(new IdentityUser(
                "concurrent-recovery-" + UUID.randomUUID() + "@example.test",
                "Recuperação Concorrente",
                passwordEncoder.encode("Original concurrent recovery phrase 2026")));
        recovery.request(user.getEmail());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq(user.getEmail()), eq(user.getDisplayName()), link.capture());
        String token = URI.create(link.getValue()).getFragment().substring("token=".length());

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> first = executor.submit(() -> completeAfter(start, token, FIRST_PASSWORD));
            Future<Outcome> second = executor.submit(() -> completeAfter(start, token, SECOND_PASSWORD));
            List<Outcome> outcomes = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder(Outcome.SUCCEEDED, Outcome.REJECTED_REPLAY);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        IdentityUser recovered = users.findById(user.getId()).orElseThrow();
        boolean firstWon = passwordEncoder.matches(FIRST_PASSWORD, recovered.getPasswordHash());
        boolean secondWon = passwordEncoder.matches(SECOND_PASSWORD, recovered.getPasswordHash());
        assertThat(firstWon ^ secondWon).isTrue();
        assertThat(recovered.getCredentialVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from password_recovery_token where user_id = ? and consumed_at is not null",
                Integer.class,
                user.getId())).isEqualTo(1);
    }

    private Outcome completeAfter(CyclicBarrier start, String token, String password) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        try {
            recovery.complete(token, password);
            return Outcome.SUCCEEDED;
        } catch (InvalidPasswordRecoveryTokenException exception) {
            return Outcome.REJECTED_REPLAY;
        }
    }

    private enum Outcome {
        SUCCEEDED,
        REJECTED_REPLAY
    }
}
