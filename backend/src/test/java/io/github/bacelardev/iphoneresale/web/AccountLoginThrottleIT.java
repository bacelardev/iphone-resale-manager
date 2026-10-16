package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Two-level account throttle over the real filter chain, PostgreSQL and Argon2. The client limit is
 * raised so it never interferes; every test uses its own usernames and client addresses, so the
 * tests share no counters and do not depend on execution order.
 */
@TestPropertySource(properties = {
        "app.auth.login-max-attempts=100",
        "app.auth.login-account-client-max-failures=5",
        "app.auth.login-account-max-failures=11"
})
class AccountLoginThrottleIT extends PostgresIntegrationTest {

    private static final int CLIENT_LIMIT = 5;
    private static final int ACCOUNT_LIMIT = 11;
    private static final AtomicInteger CLIENTS = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void fiveFailuresBlockTheAccountOnlyForThatClient() throws Exception {
        Account account = createAccount();
        String attacker = client();
        for (int attempt = 0; attempt < CLIENT_LIMIT; attempt++) {
            assertThat(login(attacker, account.username(), "invalid-password-value").getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse blocked = login(attacker, account.username(), account.password());
        assertRateLimited(blocked);
        assertThat(login(client(), account.username(), account.password()).getStatus()).isEqualTo(200);
    }

    @Test
    void oneClientRepeatingFailuresAndRejectedRequestsCannotBlockTheAccountForOthers() throws Exception {
        Account account = createAccount();
        String attacker = client();
        for (int attempt = 0; attempt < CLIENT_LIMIT; attempt++) {
            assertThat(login(attacker, account.username(), "invalid-password-value").getStatus()).isEqualTo(401);
        }
        for (int rejected = 0; rejected < 3 * ACCOUNT_LIMIT; rejected++) {
            assertThat(login(attacker, account.username(), "invalid-password-value").getStatus()).isEqualTo(429);
        }

        assertThat(login(client(), account.username(), account.password()).getStatus()).isEqualTo(200);
    }

    @Test
    void distributedFailuresReachTheAccountLimitAndBlockEveryClient() throws Exception {
        Account account = createAccount();
        Account bystander = createAccount();
        for (int failure = 0; failure < ACCOUNT_LIMIT; failure++) {
            assertThat(login(client(), account.username(), "invalid-password-value").getStatus()).isEqualTo(401);
        }

        assertRateLimited(login(client(), account.username(), account.password()));
        assertThat(login(client(), bystander.username(), bystander.password()).getStatus()).isEqualTo(200);
    }

    @Test
    void successfulLoginDoesNotResetTheAccountCounter() throws Exception {
        Account account = createAccount();
        for (int failure = 0; failure < ACCOUNT_LIMIT - 1; failure++) {
            assertThat(login(client(), account.username(), "invalid-password-value").getStatus()).isEqualTo(401);
        }
        assertThat(login(client(), account.username(), account.password()).getStatus()).isEqualTo(200);

        assertThat(login(client(), account.username(), "invalid-password-value").getStatus())
                .as("failure number %d", ACCOUNT_LIMIT).isEqualTo(401);
        assertRateLimited(login(client(), account.username(), account.password()));
    }

    @Test
    void usernameVariantsShareTheSameCountersAndInvalidFormatsNeverAuthenticate() throws Exception {
        Account account = createAccount();
        String client = client();
        String upper = account.username().toUpperCase(java.util.Locale.ROOT);
        for (String variant : new String[]{upper, " " + account.username() + " ", account.username(),
                "  " + upper, account.username() + "  "}) {
            assertThat(login(client, variant, "invalid-password-value").getStatus()).isEqualTo(401);
        }
        assertRateLimited(login(client, account.username(), account.password()));

        for (String invalid : new String[]{account.username() + "!", account.username() + " x",
                "ü" + account.username()}) {
            assertThat(login(client, invalid, account.password()).getStatus()).isEqualTo(400);
        }
    }

    @Test
    void unknownAndExistingUsernamesReceiveIdenticalPublicResponses() throws Exception {
        Account existing = createAccount();
        String unknown = "ghost." + UUID.randomUUID().toString().substring(0, 8);
        String existingClient = client();
        String unknownClient = client();

        for (int attempt = 0; attempt <= CLIENT_LIMIT; attempt++) {
            MockHttpServletResponse forExisting = login(existingClient, existing.username(), "invalid-password-value");
            MockHttpServletResponse forUnknown = login(unknownClient, unknown, "invalid-password-value");

            assertThat(forUnknown.getStatus()).isEqualTo(forExisting.getStatus());
            assertThat(forUnknown.getHeader(HttpHeaders.RETRY_AFTER) != null)
                    .isEqualTo(forExisting.getHeader(HttpHeaders.RETRY_AFTER) != null);
            assertThat(publicBody(forUnknown)).isEqualTo(publicBody(forExisting));
        }
    }

    private void assertRateLimited(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(Long.parseLong(response.getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.path("code").asText()).isEqualTo("LOGIN_RATE_LIMITED");
        assertThat(body.path("message").asText())
                .isEqualTo("Muitas tentativas de autenticação. Tente novamente mais tarde.");
    }

    private JsonNode publicBody(MockHttpServletResponse response) throws Exception {
        ObjectNode body = (ObjectNode) objectMapper.readTree(response.getContentAsString());
        body.remove("timestamp");
        body.remove("requestId");
        return body;
    }

    private MockHttpServletResponse login(String client, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(client);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn().getResponse();
    }

    private Account createAccount() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Account account = new Account("throttle." + suffix, "throttle-password-" + suffix);
        MockHttpServletResponse admin = login(client(), TEST_USERNAME, TEST_PASSWORD);
        assertThat(admin.getStatus()).isEqualTo(200);
        String token = objectMapper.readTree(admin.getContentAsString()).path("accessToken").asText();
        MockHttpServletResponse created = mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Conta " + suffix, "username", account.username(),
                                "password", account.password()))))
                .andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        return account;
    }

    private static String client() {
        int next = CLIENTS.incrementAndGet();
        return "10.20." + (next >> 8 & 255) + "." + (next & 255);
    }

    private record Account(String username, String password) {
    }
}
