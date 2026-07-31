package com.karamba121.backend.features.session;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenHistoryRepository extends JpaRepository<RefreshTokenHistory, String> {
    List<RefreshTokenHistory> findAllByFamilyId(String familyId);
}
