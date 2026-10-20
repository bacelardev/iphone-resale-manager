package io.github.bacelardev.iphoneresale.web;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Per-client limit with the default forwarding strategy. Every test uses its own usernames and
 * client addresses, and the bootstrap account is only ever used for successful logins, so no test
 * depends on counters left by another one or on the execution order.
 */
@TestPropertySource(properties = "app.auth.login-max-attempts=2")
class LoginRateLimitIT extends PostgresIntegrationTest {

    private static final String RATE_LIMIT_MESSAGE =
            "Muitas tentativas de autenticação. Tente novamente mais tarde.";
    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void limitsByDirectPeerAddressAndDoesNotTrustForwardedFor() throws Exception {
        String client = client();
        String username = unique("peer.limit");

        login(client, username, "invalid-password-value", "198.51.100.1")
                .andExpect(status().isUnauthorized());
        login(client, username, "invalid-password-value", "198.51.100.2")
                .andExpect(status().isUnauthorized());
        login(client, username, "invalid-password-value", "198.51.100.3")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value(RATE_LIMIT_MESSAGE));
    }

    @Test
    void successfulLoginClearsTheClientWindow() throws Exception {
        String username = unique("window.reset");
        String password = "window-reset-password-" + UUID.randomUUID();
        createUser(username, password);
        String client = client();

        login(client, username, "invalid-password-value", null).andExpect(status().isUnauthorized());
        login(client, username, password, null).andExpect(status().isOk());
        login(client, username, "invalid-password-value", null).andExpect(status().isUnauthorized());
        login(client, username, "invalid-password-value", null).andExpect(status().isUnauthorized());
        login(client, username, "invalid-password-value", null).andExpect(status().isTooManyRequests());
    }

    @Test
    void clientLimitAppliesAcrossDifferentUsernames() throws Exception {
        String client = client();

        login(client, unique("spray.a"), "invalid-password-value", null).andExpect(status().isUnauthorized());
        login(client, unique("spray.b"), "invalid-password-value", null).andExpect(status().isUnauthorized());
        login(client, unique("spray.c"), "invalid-password-value", null)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        login(client(), unique("spray.d"), "invalid-password-value", null).andExpect(status().isUnauthorized());
    }

    private void createUser(String username, String password) throws Exception {
        String admin = objectMapper.readTree(login(client(), TEST_USERNAME, TEST_PASSWORD, null)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .path("accessToken").asText();
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Conta " + username, "username", username, "password", password))))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String client, String username, String password, String forwardedFor)
            throws Exception {
        var request = post("/api/v1/auth/login")
                .with(remoteAddress(client))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password)));
        if (forwardedFor != null) request.header("X-Forwarded-For", forwardedFor);
        return mockMvc.perform(request);
    }

    private static String unique(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String client() {
        int next = CLIENTS.incrementAndGet();
        return "10.30." + (next >> 8 & 255) + "." + (next & 255);
    }

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
