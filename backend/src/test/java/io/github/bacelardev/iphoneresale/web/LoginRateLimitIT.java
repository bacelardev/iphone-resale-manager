package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.auth.login-max-attempts=2",
        "app.auth.login-account-max-attempts=5"
})
class LoginRateLimitIT extends PostgresIntegrationTest {

    private static final String RATE_LIMIT_MESSAGE =
            "Muitas tentativas de autenticação. Tente novamente mais tarde.";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void limitsByDirectPeerAddressAndDoesNotTrustForwardedFor() throws Exception {
        String body = """
                {"username":"%s","password":"invalid-password-value"}
                """.formatted(TEST_USERNAME);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("192.0.2.10"))
                        .header("X-Forwarded-For", "198.51.100.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("192.0.2.10"))
                        .header("X-Forwarded-For", "198.51.100.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("192.0.2.10"))
                        .header("X-Forwarded-For", "198.51.100.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value(RATE_LIMIT_MESSAGE));
    }

    @Test
    void successfulLoginClearsTheClientWindow() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("192.0.2.20"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"invalid-password-value"}
                                """.formatted(TEST_USERNAME)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("192.0.2.20"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(TEST_USERNAME, TEST_PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(remoteAddress("192.0.2.20"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"invalid-password-value"}
                                """.formatted(TEST_USERNAME)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void limitsOneAccountAcrossRotatingClientAddresses() throws Exception {
        for (int client = 1; client <= 5; client++) {
            login("192.0.2.10" + client, " Rotating.Target ", "invalid-password-value")
                    .andExpect(status().isUnauthorized());
        }
        login("192.0.2.106", "rotating.target", "invalid-password-value")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value(RATE_LIMIT_MESSAGE));
    }

    @Test
    void blockedAccountRejectsItsOwnCorrectPasswordButLeavesOtherAccountsUsable() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String victim = "rl.victim." + suffix;
        String victimPassword = "victim-password-" + suffix;
        String adminToken = token(login("192.0.2.120", TEST_USERNAME, TEST_PASSWORD)
                .andExpect(status().isOk()));
        mockMvc.perform(post("/api/v1/users")
                        .with(remoteAddress("192.0.2.120"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Vítima Rate Limit", "username", victim, "password", victimPassword))))
                .andExpect(status().isCreated());

        for (int client = 1; client <= 5; client++) {
            login("192.0.2.13" + client, victim, "invalid-password-value")
                    .andExpect(status().isUnauthorized());
        }
        login("192.0.2.140", victim, victimPassword)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));

        login("192.0.2.141", TEST_USERNAME, TEST_PASSWORD).andExpect(status().isOk());
        login("192.0.2.142", TEST_USERNAME, "invalid-password-value")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private ResultActions login(String client, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(remoteAddress(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private String token(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.path("accessToken").asText();
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
