package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the production proxy settings from render.yaml: the framework strategy registers
 * Spring's ForwardedHeaderFilter and one trusted proxy appends the client address to
 * X-Forwarded-For. Requests travel over real HTTP through the embedded Tomcat, so the TCP peer is
 * always loopback and each "client" is simulated by the proxy-written rightmost entry. This
 * validates the application's trust boundary, not the actual header layout of Render's proxy.
 */
@TestPropertySource(properties = {
        "server.forward-headers-strategy=framework",
        "app.auth.login-trusted-proxy-hops=1",
        "app.auth.login-max-attempts=3",
        "app.auth.login-window=PT1M",
        "app.auth.login-account-max-attempts=5"
})
class ForwardedLoginRateLimitIT extends PostgresIntegrationTest {

    @Autowired private TestRestTemplate http;
    @Autowired private MutableClock clock;

    @Test
    void rotatingClientSuppliedForwardedForDoesNotBypassTheClientLimit() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertStatus(login("spoof.xff", forwardedFor("198.51.100." + attempt + ", 203.0.113.10")), 401);
        }
        ResponseEntity<JsonNode> blocked = login("spoof.xff", forwardedFor("198.51.100.4, 203.0.113.10"));
        assertStatus(blocked, 429);
        assertThat(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(body(blocked).path("code").asText()).isEqualTo("LOGIN_RATE_LIMITED");
    }

    @Test
    void rotatingForwardedHeaderDoesNotBypassTheClientLimit() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpHeaders headers = forwardedFor("203.0.113.20");
            headers.set("Forwarded", "for=198.51.100." + attempt);
            assertStatus(login("spoof.forwarded", headers), 401);
        }
        HttpHeaders headers = forwardedFor("203.0.113.20");
        headers.set("Forwarded", "for=198.51.100.4");
        assertStatus(login("spoof.forwarded", headers), 429);
    }

    @Test
    void repeatedForwardedForLinesAreReadInOrderAndOnlyTheProxyEntryCounts() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            HttpHeaders headers = new HttpHeaders();
            headers.add("X-Forwarded-For", "198.51.100." + attempt + ", 198.51.100.9" + attempt);
            headers.add("X-Forwarded-For", "203.0.113.25");
            assertStatus(login("spoof.lines", headers), 401);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", "198.51.100.4");
        headers.add("X-Forwarded-For", "203.0.113.25");
        assertStatus(login("spoof.lines", headers), 429);
    }

    @Test
    void distinctClientsBehindTheProxyKeepIndependentBucketsThatCannotBeImpersonated() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertStatus(login("shared.proxy.a", forwardedFor("203.0.113.30")), 401);
        }
        assertStatus(login("shared.proxy.a", forwardedFor("203.0.113.30")), 429);
        // Another client is not penalised for sharing the proxy...
        assertStatus(login("shared.proxy.b", forwardedFor("203.0.113.31")), 401);
        // ...and the blocked client cannot claim the other client's address.
        assertStatus(login("shared.proxy.a", forwardedFor("203.0.113.31, 203.0.113.30")), 429);
    }

    @Test
    void limitsOneAccountAcrossManyRealClientAddresses() {
        for (int client = 1; client <= 5; client++) {
            assertStatus(login("distributed.target", forwardedFor("203.0.113.6" + client)), 401);
        }
        ResponseEntity<JsonNode> blocked = login("distributed.target", forwardedFor("203.0.113.70"));
        assertStatus(blocked, 429);
        assertThat(body(blocked).path("code").asText()).isEqualTo("LOGIN_RATE_LIMITED");
        assertStatus(login("distributed.other", forwardedFor("203.0.113.71")), 401);
    }

    @Test
    void legitimateLoginBehindTheProxySucceedsAndClearsOnlyThatClientBucket() {
        for (int attempt = 1; attempt <= 2; attempt++) {
            assertStatus(login("behind.proxy", forwardedFor("203.0.113.40")), 401);
        }
        HttpHeaders headers = forwardedFor("198.51.100.200, 203.0.113.40");
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> success = http.postForEntity("/api/v1/auth/login", new HttpEntity<>(
                Map.of("username", TEST_USERNAME, "password", TEST_PASSWORD), headers), JsonNode.class);
        assertStatus(success, 200);
        assertThat(body(success).path("accessToken").asText()).isNotBlank();

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertStatus(login("behind.proxy", forwardedFor("203.0.113.40")), 401);
        }
        assertStatus(login("behind.proxy", forwardedFor("203.0.113.40")), 429);
    }

    @Test
    void releasesTheClientLimitOnceTheWindowExpires() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertStatus(login("window.user", forwardedFor("203.0.113.50")), 401);
        }
        assertStatus(login("window.user", forwardedFor("203.0.113.50")), 429);

        clock.advance(Duration.ofMinutes(1));

        assertStatus(login("window.user", forwardedFor("203.0.113.50")), 401);
    }

    private ResponseEntity<JsonNode> login(String username, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity("/api/v1/auth/login", new HttpEntity<>(
                Map.of("username", username, "password", "invalid-password-value"), headers), JsonNode.class);
    }

    private static HttpHeaders forwardedFor(String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", value);
        return headers;
    }

    private static void assertStatus(ResponseEntity<JsonNode> response, int expected) {
        assertThat(response.getStatusCode().value()).isEqualTo(expected);
    }

    private static JsonNode body(ResponseEntity<JsonNode> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
