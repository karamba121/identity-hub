package com.karamba121.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "identity-hub.abuse-protection.subject-limit=3",
        "identity-hub.abuse-protection.origin-limit=10",
        "identity-hub.abuse-protection.combination-limit=2"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitHttpIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns429WithRetryAfterWithoutDependingOnlyOnOrigin() throws Exception {
        String email = "limited-" + UUID.randomUUID() + "@example.test";
        requestRecovery(email, "192.0.2.50").andExpect(status().isAccepted());
        requestRecovery(email, "192.0.2.50").andExpect(status().isAccepted());
        requestRecovery(email, "192.0.2.50")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.detail")
                        .value("Muitas tentativas. Aguarde antes de tentar novamente."));

        requestRecovery("other-" + UUID.randomUUID() + "@example.test", "192.0.2.50")
                .andExpect(status().isAccepted());
    }

    private org.springframework.test.web.servlet.ResultActions requestRecovery(String email, String origin)
            throws Exception {
        return mockMvc.perform(post("/api/v1/password-recovery")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(origin);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }
}
