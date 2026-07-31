package com.karamba121.backend.features.access;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionDefinitionRepository extends JpaRepository<PermissionDefinition, String> {

    List<PermissionDefinition> findAllByOrderBySortOrderAsc();
}
