package com.karamba121.backend.features.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface UserMfaRepository extends JpaRepository<UserMfa, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mfa from UserMfa mfa where mfa.userId = :userId")
    Optional<UserMfa> findByUserIdForUpdate(String userId);
}
