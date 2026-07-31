package com.karamba121.backend.features.resource;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoResourceController {

    @GetMapping("/resource")
    DemoResourceResponse resource(@AuthenticationPrincipal Jwt jwt) {
        List<String> scopes = jwt.getClaimAsStringList("scope");
        if (scopes == null) {
            scopes = List.of();
        }
        return new DemoResourceResponse(
                "Acesso autorizado à API protegida",
                jwt.getSubject(),
                jwt.getAudience(),
                scopes);
    }

    record DemoResourceResponse(
            String message,
            String subject,
            List<String> audience,
            List<String> scopes) {
    }
}
