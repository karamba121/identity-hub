package com.karamba121.backend.features.access;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AdministrativeBootstrapLockRepository
        extends JpaRepository<AdministrativeBootstrapLock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bootstrapLock from AdministrativeBootstrapLock bootstrapLock "
            + "where bootstrapLock.lockName = :lockName")
    Optional<AdministrativeBootstrapLock> findByLockNameForUpdate(@Param("lockName") String lockName);
}
