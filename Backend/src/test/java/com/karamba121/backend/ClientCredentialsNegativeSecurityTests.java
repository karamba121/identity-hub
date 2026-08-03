package com.karamba121.backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.karamba121.backend.features.access.AdminResourceContract;
import com.karamba121.backend.features.resource.DemoResourceContract;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClientCredentialsNegativeSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredClientRepository clients;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void rejectsInvalidClientSecretWithoutLeakingCredentialOrIssuingTokens() throws Exception {
        MachineClient client = machineClient();

        token(client.clientId(), "incorrect-" + client.secret(), DemoResourceContract.SCOPE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andExpect(content().string(not(containsString(client.secret()))))
                .andExpect(content().string(not(containsString(client.clientId()))));
    }

    @Test
    void rejectsScopesBeyondTheExplicitMachineGrant() throws Exception {
        MachineClient client = machineClient();

        token(
                client.clientId(),
                client.secret(),
                DemoResourceContract.SCOPE + " " + AdminResourceContract.SCOPE)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.refresh_token").doesNotExist());
    }

    @Test
    void machineTokenCannotCrossFromDemoAudienceIntoAdministrativeApi() throws Exception {
        MachineClient client = machineClient();
        MvcResult tokenResponse = token(client.clientId(), client.secret(), DemoResourceContract.SCOPE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value(DemoResourceContract.SCOPE))
                .andReturn();
        String accessToken = JsonPath.read(tokenResponse.getResponse().getContentAsString(), "$.access_token");

        mockMvc.perform(get("/api/v1/demo/resource")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("client:" + client.clientId()));
        mockMvc.perform(get("/api/v1/admin/context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administrativeAudienceCannotBeConfusedWithDemoAudienceOrScope() throws Exception {
        String adminAudienceWithDemoScope = signedToken(
                AdminResourceContract.AUDIENCE,
                DemoResourceContract.SCOPE);

        mockMvc.perform(get("/api/v1/demo/resource")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAudienceWithDemoScope))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/context")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminAudienceWithDemoScope))
                .andExpect(status().isForbidden());
    }

    private MachineClient machineClient() {
        String suffix = UUID.randomUUID().toString();
        String clientId = "negative-machine-" + suffix;
        String secret = "machine-secret-" + suffix;
        RegisteredClient registered = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(secret))
                .clientName("Cliente negativo")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(DemoResourceContract.SCOPE)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build();
        clients.save(registered);
        return new MachineClient(clientId, secret);
    }

    private ResultActions token(String clientId, String secret, String scope) throws Exception {
        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(post("/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("scope", scope));
    }

    private String signedToken(String audience, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject("client:audience-confusion")
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    private record MachineClient(String clientId, String secret) {
    }
}
