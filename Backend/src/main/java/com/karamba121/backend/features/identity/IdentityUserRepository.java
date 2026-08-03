package com.karamba121.backend.features.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface IdentityUserRepository extends JpaRepository<IdentityUser, String> {

    Optional<IdentityUser> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from IdentityUser user where user.id = :id")
    Optional<IdentityUser> findByIdForUpdate(String id);
}
