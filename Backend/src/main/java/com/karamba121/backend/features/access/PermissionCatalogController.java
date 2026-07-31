package com.karamba121.backend.features.access;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/permission-catalog")
public class PermissionCatalogController {

    private static final String CATALOG_NOTICE =
            "O catálogo descreve capacidades disponíveis; ele não representa permissões concedidas ao usuário.";

    private final PermissionDefinitionRepository permissions;

    public PermissionCatalogController(PermissionDefinitionRepository permissions) {
        this.permissions = permissions;
    }

    @GetMapping
    PermissionCatalogResponse list() {
        List<PermissionDefinitionResponse> definitions = permissions.findAllByOrderBySortOrderAsc()
                .stream()
                .map(permission -> new PermissionDefinitionResponse(
                        permission.getCode(),
                        permission.getDisplayName(),
                        permission.getDescription(),
                        permission.getCategory().name()))
                .toList();
        return new PermissionCatalogResponse(CATALOG_NOTICE, definitions);
    }

    record PermissionCatalogResponse(String notice, List<PermissionDefinitionResponse> permissions) {
    }

    record PermissionDefinitionResponse(
            String code,
            String displayName,
            String description,
            String category) {
    }
}
