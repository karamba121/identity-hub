package com.karamba121.backend.features.identity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FederatedIdentityRepository extends JpaRepository<FederatedIdentity, String> {

    Optional<FederatedIdentity> findByProviderRegistrationIdAndProviderSubject(
            String providerRegistrationId, String providerSubject);

    Optional<FederatedIdentity> findByIdAndUserEmailIgnoreCase(String id, String email);

    boolean existsByUserIdAndProviderRegistrationId(String userId, String providerRegistrationId);

    long countByUserId(String userId);

    List<FederatedIdentity> findAllByUserEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
