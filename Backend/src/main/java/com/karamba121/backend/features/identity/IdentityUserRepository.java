package com.karamba121.backend.features.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityUserRepository extends JpaRepository<IdentityUser, String> {

    Optional<IdentityUser> findByEmailIgnoreCase(String email);
}
