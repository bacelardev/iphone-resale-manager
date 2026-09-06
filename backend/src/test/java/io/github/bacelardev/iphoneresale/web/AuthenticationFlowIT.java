package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenGenerator;
import io.github.bacelardev.iphoneresale.application.port.security.AccessTokenHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import java.util.Map;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.auth.login-max-attempts=100")
class AuthenticationFlowIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AccessTokenGenerator tokenGenerator;
    @Autowired private AccessTokenHasher tokenHasher;
    @Autowired private TestRestTemplate httpClient;
    @Autowired private ApplicationContext context;

    @Test
    void fullFlowUsesRealHttpAndDefaultCredentialsDoNotExist() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("validate");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> login = httpClient.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("username", TEST_USERNAME, "password", TEST_PASSWORD), headers),
                JsonNode.class);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(login.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(login.getHeaders().containsKey(HttpHeaders.SET_COOKIE)).isFalse();
        headers.setBearerAuth(login.getBody().path("accessToken").asText());
        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<JsonNode> me = httpClient.exchange("/api/v1/auth/me", HttpMethod.GET,
                request, JsonNode.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().path("username").asText()).isEqualTo(TEST_USERNAME);
        assertThat(httpClient.exchange("/api/v1/auth/logout", HttpMethod.POST, request, Void.class)
                .getStatusCode().value()).isEqualTo(204);
        assertThat(httpClient.exchange("/api/v1/auth/me", HttpMethod.GET, request, JsonNode.class)
                .getStatusCode().value()).isEqualTo(401);
        assertThat(httpClient.exchange("/api/v1/auth/logout", HttpMethod.POST, request, Void.class)
                .getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void requestIdRequiresCanonicalUuidAndIsReusedForCorrelation() throws Exception {
        String requestId = UUID.randomUUID().toString();
        mockMvc.perform(get("/api/v1/auth/me").header("X-Request-Id", requestId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
        for (String invalidId : new String[]{"", "1-1-1-1-1", " "}) {
            mockMvc.perform(get("/api/v1/auth/me").header("X-Request-Id", invalidId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST_ID"));
        }
    }

    @Test
    void loginNormalizesUsernameAndRejectsInvalidInputs() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "  " + TEST_USERNAME.toUpperCase(java.util.Locale.ROOT) + "  ",
                                "password", TEST_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(TEST_USERNAME));
        for (Map<String, String> input : java.util.List.of(
                Map.of("username", "bad@username", "password", TEST_PASSWORD),
                Map.of("username", TEST_USERNAME, "password", "short"),
                Map.of("username", TEST_USERNAME, "password", "x".repeat(129)))) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(input)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void loginPersistsOnlySha256AndResponseNeverExposesInternalFields() throws Exception {
        JsonNode login = login();
        String rawToken = login.path("accessToken").asText();

        assertThat(rawToken).matches("^irs_[A-Za-z0-9_-]{43}$");
        assertThat(login.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(Instant.parse(login.path("expiresAt").asText())).isAfter(Instant.now());
        assertThat(login.toString()).doesNotContain("password", "passwordHash", "tokenHash");

        Integer storedHash = jdbcTemplate.queryForObject(
                "select count(*) from auth_session where token_hash = ?",
                Integer.class,
                tokenHasher.hash(rawToken)
        );
        Integer rawStored = jdbcTemplate.queryForObject(
                "select count(*) from auth_session where token_hash = ?",
                Integer.class,
                rawToken
        );
        assertThat(storedHash).isEqualTo(1);
        assertThat(rawStored).isZero();
    }

    @Test
    void meUsesBearerSessionAndReturnsTheExplicitUserDto() throws Exception {
        String token = login().path("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.username").value(TEST_USERNAME))
                .andExpect(jsonPath("$.role").value("SOCIO"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void missingMalformedUnknownExpiredAndInactiveCredentialsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer realm=\"iphone-resale\""));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGenerator.generate()))
                .andExpect(status().isUnauthorized());

        String expiredToken = tokenGenerator.generate();
        UUID userId = jdbcTemplate.queryForObject(
                "select id from app_user where username = ?", UUID.class, TEST_USERNAME);
        jdbcTemplate.update("""
                insert into auth_session (user_id, token_hash, created_at, expires_at)
                values (?, ?, now() - interval '2 hours', now() - interval '1 hour')
                """, userId, tokenHasher.hash(expiredToken));
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());

        try {
            jdbcTemplate.update("update app_user set active = false where id = ?", userId);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validLoginJson()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("Usuário ou senha inválidos."));
        } finally {
            jdbcTemplate.update("update app_user set active = true where id = ?", userId);
        }
    }

    @Test
    void allLoginFailuresHaveTheSamePublicShape() throws Exception {
        MvcResult missing = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"missing.user","password":"invalid-password-value"}
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn();
        MvcResult wrong = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"invalid-password-value"}
                                """.formatted(TEST_USERNAME)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        JsonNode missingBody = objectMapper.readTree(missing.getResponse().getContentAsString());
        JsonNode wrongBody = objectMapper.readTree(wrong.getResponse().getContentAsString());
        assertThat(missingBody.path("code")).isEqualTo(wrongBody.path("code"));
        assertThat(missingBody.path("message")).isEqualTo(wrongBody.path("message"));
    }

    @Test
    void logoutRevokesPersistentlyAndIsRepeatableForAWellFormedToken() throws Exception {
        String token = login().path("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());

        java.sql.Timestamp revokedAt = jdbcTemplate.queryForObject(
                "select revoked_at from auth_session where token_hash = ?",
                java.sql.Timestamp.class,
                tokenHasher.hash(token)
        );
        assertThat(revokedAt).isNotNull();
    }

    @Test
    void logoutRequiresBearerButAcceptsUnknownWellFormedToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenGenerator.generate()))
                .andExpect(status().isNoContent());
    }

    @Test
    void rejectsUnknownJsonAndInvalidRequestIdWithoutEchoingInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","admin":true}
                                """.formatted(TEST_USERNAME, TEST_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Request-Id", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson()))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", matchesPattern(
                        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_ID"));
    }

    @Test
    void corsIsAnExplicitCredentialFreeAllowlistAndSecurityHeadersArePresent() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://app.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Authorization,Content-Type,X-Request-Id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://app.example.test"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    private JsonNode login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validLoginJson()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String validLoginJson() {
        return """
                {"username":"%s","password":"%s"}
                """.formatted(TEST_USERNAME, TEST_PASSWORD);
    }
}
