package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.bacelardev.iphoneresale.application.port.security.PasswordHashService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "app.auth.login-max-attempts=100")
class PasswordHashingIT extends PostgresIntegrationTest {

    @Autowired private TestRestTemplate httpClient;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordHashService passwordHashService;

    @Test
    void bootstrapPersistsArgon2idFor128CharactersAndAuthenticatesOverHttp() {
        assertThat(TEST_PASSWORD).hasSize(128);
        String storedHash = jdbc.queryForObject(
                "select password_hash from app_user where username = ?", String.class, TEST_USERNAME);
        assertThat(storedHash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$").hasSize(97);
        assertThat(jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_schema = 'public' and table_name = 'app_user' and column_name = 'password_hash'
                """, Integer.class)).isEqualTo(255);
        assertSuccessfulLogin(TEST_USERNAME, TEST_PASSWORD, storedHash);
    }

    @ParameterizedTest(name = "HTTP supported password fixture {index}")
    @MethodSource("supportedPasswords")
    void acceptsContractBoundariesAndUnicodeWithoutTruncation(String password) {
        String username = "test." + UUID.randomUUID();
        String hash = passwordHashService.encode(password);
        // Fixture only: no user-creation use case or public endpoint is introduced.
        jdbc.update("""
                insert into app_user (name, username, password_hash, role, active)
                values (?, ?, ?, 'SOCIO', true)
                """, "Password contract fixture", username, hash);
        assertSuccessfulLogin(username, password, hash);
        String wrong = password.substring(0, password.offsetByCodePoints(password.length(), -1)) + "!";
        assertGenericFailure(login(username, wrong), password, hash);
    }

    @ParameterizedTest
    @ValueSource(ints = {11, 129})
    void rejectsPasswordsOutsideTheContract(int length) {
        String password = "x".repeat(length);
        ResponseEntity<JsonNode> response = login(TEST_USERNAME, password);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertNoSecrets(response.getBody(), password, null);
    }

    @Test
    void missingWrongAndInactiveLoginsHaveIdenticalPublicErrorsAndNoHashes() {
        String requestId = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", requestId);
        ResponseEntity<JsonNode> missing = login("missing.user", TEST_PASSWORD, headers);
        ResponseEntity<JsonNode> wrong = login(TEST_USERNAME, "wrong-value-".repeat(10), headers);
        ResponseEntity<JsonNode> inactive;
        try {
            jdbc.update("update app_user set active = false where username = ?", TEST_USERNAME);
            inactive = login(TEST_USERNAME, TEST_PASSWORD, headers);
        } finally {
            jdbc.update("update app_user set active = true where username = ?", TEST_USERNAME);
        }
        String storedHash = jdbc.queryForObject(
                "select password_hash from app_user where username = ?", String.class, TEST_USERNAME);
        for (ResponseEntity<JsonNode> response : java.util.List.of(missing, wrong, inactive)) {
            assertGenericFailure(response, TEST_PASSWORD, storedHash);
            // The timestamp is naturally different; all remaining public fields are identical.
            ((com.fasterxml.jackson.databind.node.ObjectNode) response.getBody()).remove("timestamp");
        }
        assertThat(missing.getBody()).isEqualTo(wrong.getBody()).isEqualTo(inactive.getBody());
    }

    static Stream<String> supportedPasswords() {
        return Stream.of("a".repeat(12), "b".repeat(127), "c".repeat(128),
                "á漢ç€".repeat(32), "🔐".repeat(60));
    }

    private void assertSuccessfulLogin(String username, String password, String hash) {
        ResponseEntity<JsonNode> login = login(username, password);
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertNoSecrets(login.getBody(), password, hash);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().path("accessToken").asText());
        ResponseEntity<JsonNode> me = httpClient.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), JsonNode.class);
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        assertThat(me.getBody().path("username").asText()).isEqualTo(username);
        assertThat(me.getBody().path("role").asText()).isEqualTo("SOCIO");
        assertNoSecrets(me.getBody(), password, hash);
    }

    private void assertGenericFailure(ResponseEntity<JsonNode> response, String password, String hash) {
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().path("code").asText()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(response.getBody().path("message").asText()).isEqualTo("Usuário ou senha inválidos.");
        assertNoSecrets(response.getBody(), password, hash);
    }

    private void assertNoSecrets(JsonNode body, String password, String hash) {
        assertThat(body.toString()).doesNotContain(password, "passwordHash", "password_hash",
                "dummyHash", "dummyPasswordHash", "$argon2", "dummy-authentication-value");
        // Validation errors may name the password field but must never echo its value.
        assertThat(body.has("password")).isFalse();
        assertThat(body.path("user").has("password")).isFalse();
        if (hash != null) {
            assertThat(body.toString()).doesNotContain(hash);
        }
    }

    private ResponseEntity<JsonNode> login(String username, String password) {
        return login(username, password, new HttpHeaders());
    }

    private ResponseEntity<JsonNode> login(String username, String password, HttpHeaders headers) {
        return httpClient.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", password), headers), JsonNode.class);
    }
}
