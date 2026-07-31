package com.karamba121.backend.features.session;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, String> {

    Optional<RefreshTokenFamily> findByAuthorizationId(String authorizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select family from RefreshTokenFamily family where family.id = :id")
    Optional<RefreshTokenFamily> findByIdForUpdate(@Param("id") String id);
}
