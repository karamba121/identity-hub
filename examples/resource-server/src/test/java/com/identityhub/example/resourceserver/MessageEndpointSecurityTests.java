package com.identityhub.example.resourceserver;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MessageEndpointSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "resourceServerJwtDecoder")
    private JwtDecoder jwtDecoder;

    @Test
    void requiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsMachineIdentityForValidToken() throws Exception {
        when(jwtDecoder.decode("valid-token")).thenReturn(jwt("demo.read"));

        mockMvc.perform(get("/api/v1/messages")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("client:example-client"))
                .andExpect(jsonPath("$.audience[0]").value("identity-hub-api"));
    }

    @Test
    void rejectsTokenWithoutRequiredScope() throws Exception {
        when(jwtDecoder.decode("insufficient-token")).thenReturn(jwt("openid"));

        mockMvc.perform(get("/api/v1/messages")
                        .header("Authorization", "Bearer insufficient-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsTokenWhenDecoderCannotValidateIt() throws Exception {
        when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("assinatura inválida"));

        mockMvc.perform(get("/api/v1/messages")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    private static Jwt jwt(String scope) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("decoded-token")
                .header("alg", "RS256")
                .issuer("http://localhost:4200")
                .subject("client:example-client")
                .audience(List.of("identity-hub-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .build();
    }
}
