package com.karamba121.backend.features.interaction;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationInteractionRepository
        extends JpaRepository<AuthorizationInteraction, String> {

    Optional<AuthorizationInteraction> findByIdHashAndSessionIdHash(String idHash, String sessionIdHash);
}
