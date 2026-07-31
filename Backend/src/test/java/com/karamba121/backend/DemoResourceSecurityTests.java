package com.karamba121.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.karamba121.backend.features.resource.DemoResourceContract;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoResourceSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void requiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/demo/resource"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenIssuedForAnotherAudience() throws Exception {
        mockMvc.perform(get("/api/v1/demo/resource")
                        .header("Authorization", "Bearer " + token(
                                List.of("another-api"), DemoResourceContract.SCOPE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutRequiredScope() throws Exception {
        mockMvc.perform(get("/api/v1/demo/resource")
                        .header("Authorization", "Bearer " + token(
                                List.of(DemoResourceContract.AUDIENCE), "openid")))
                .andExpect(status().isForbidden());
    }

    private String token(List<String> audience, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject("security-test-user")
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
