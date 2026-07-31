package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.karamba121.backend.features.access.PermissionCode;
import com.karamba121.backend.features.resource.DemoResourceContract;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermissionCatalogIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void requiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/demo/permission-catalog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresDemoReadScope() throws Exception {
        mockMvc.perform(get("/api/v1/demo/permission-catalog")
                        .header("Authorization", "Bearer " + token("catalog-reader", "openid")))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsTheVersionedCatalogWithoutImplyingGrants() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/demo/permission-catalog")
                        .header("Authorization", "Bearer " + token(
                                "catalog-reader", DemoResourceContract.SCOPE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notice").isNotEmpty())
                .andExpect(jsonPath("$.permissions.length()").value(PermissionCode.values().length))
                .andExpect(jsonPath("$.permissions[0].granted").doesNotExist())
                .andReturn();

        List<String> codes = JsonPath.read(
                result.getResponse().getContentAsString(), "$.permissions[*].code");
        assertThat(codes)
                .containsExactly(
                        PermissionCode.TENANT_ACCESS_READ.value(),
                        PermissionCode.TENANT_ACCESS_MANAGE.value(),
                        PermissionCode.OAUTH_CLIENTS_READ.value(),
                        PermissionCode.OAUTH_CLIENTS_MANAGE.value(),
                        PermissionCode.SECURITY_AUDIT_READ.value())
                .containsExactlyInAnyOrderElementsOf(PermissionCode.valuesSet());
    }

    private String token(String subject, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject(subject)
                .audience(List.of(DemoResourceContract.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
