package com.karamba121.backend.features.identity;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface PasswordRecoveryTokenRepository extends JpaRepository<PasswordRecoveryToken, String> {

    @Query("select token.user.id from PasswordRecoveryToken token where token.tokenHash = :tokenHash")
    Optional<String> findUserIdByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from PasswordRecoveryToken token where token.tokenHash = :tokenHash")
    Optional<PasswordRecoveryToken> findByTokenHashForUpdate(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PasswordRecoveryToken token
               set token.revokedAt = :revokedAt
             where token.user.id = :userId
               and token.consumedAt is null
               and token.revokedAt is null
            """)
    int revokeActiveByUserId(String userId, Instant revokedAt);
}
