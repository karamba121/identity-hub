package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordHashUpgradeIntegrationTests {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Test
    void authenticatesLegacyBcryptAndUpgradesItToArgon2id() {
        String email = "legacy-password-" + UUID.randomUUID() + "@example.test";
        String rawPassword = "Legacy access phrase 2026";
        String legacyHash = new BCryptPasswordEncoder(12).encode(rawPassword);
        users.saveAndFlush(new IdentityUser(email, "Legacy User", legacyHash));

        assertThat(authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword)).isAuthenticated())
                .isTrue();
        entityManager.flush();
        entityManager.clear();

        String upgraded = users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
        assertThat(upgraded).startsWith("{argon2id}$argon2id$").isNotEqualTo(legacyHash);
        assertThat(passwordEncoder.matches(rawPassword, upgraded)).isTrue();
    }

    @Test
    void newHashesUseVersionedArgon2idParametersAndRandomSalts() {
        String first = passwordEncoder.encode("a sufficiently long phrase");
        String second = passwordEncoder.encode("a sufficiently long phrase");

        assertThat(first).startsWith("{argon2id}$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(second).startsWith("{argon2id}$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(first).isNotEqualTo(second);
        assertThat(passwordEncoder.matches("a sufficiently long phrase", first)).isTrue();
    }
}
