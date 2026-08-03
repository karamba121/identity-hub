package com.karamba121.backend.features.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, String> {

    long countByUserIdAndUsedAtIsNull(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from MfaRecoveryCode code where code.userId = :userId and code.codeHash = :hash and code.usedAt is null")
    Optional<MfaRecoveryCode> findAvailableForUpdate(String userId, String hash);

    @Modifying
    @Query("delete from MfaRecoveryCode code where code.userId = :userId")
    void deleteByUserId(String userId);
}
