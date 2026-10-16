package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.bacelardev.iphoneresale.web.filter.LoginClientAddressResolver;
import io.github.bacelardev.iphoneresale.web.filter.LoginRateLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs with the forwarding settings read from render.yaml (framework strategy, one trusted proxy
 * hop), so the test follows the production blueprint instead of a hand-copied value. Requests
 * travel over real HTTP through the embedded Tomcat: the TCP peer is always loopback and each
 * client is simulated by the proxy-written rightmost X-Forwarded-For entry. This validates the
 * application's trust boundary under that configuration, not the header layout of Render's proxy,
 * which only a staging check can confirm. Other production settings (the prod profile, ImageKit,
 * bootstrap disabled) are not reproduced here.
 */
@TestPropertySource(properties = {
        "app.auth.login-max-attempts=3",
        "app.auth.login-window=PT1M",
        "app.auth.login-account-client-max-failures=5",
        "app.auth.login-account-max-failures=11"
})
class ForwardedLoginRateLimitIT extends PostgresIntegrationTest {

    private static final Path RENDER_BLUEPRINT = Path.of("..", "render.yaml");

    @Autowired private TestRestTemplate http;
    @Autowired private MutableClock clock;
    @Autowired private ApplicationContext context;
    @Autowired private ServerProperties serverProperties;

    @DynamicPropertySource
    static void productionForwardingSettings(DynamicPropertyRegistry registry) {
        Map<String, String> production = renderEnvironment();
        registry.add("server.forward-headers-strategy", () -> production.get("SERVER_FORWARD_HEADERS_STRATEGY"));
        registry.add("app.auth.login-trusted-proxy-hops", () -> production.get("APP_AUTH_LOGIN_TRUSTED_PROXY_HOPS"));
    }

    @Test
    void forwardingSettingsComeFromTheProductionBlueprint() {
        Map<String, String> production = renderEnvironment();

        assertThat(production.get("SERVER_FORWARD_HEADERS_STRATEGY")).isEqualTo("framework");
        assertThat(production.get("APP_AUTH_LOGIN_TRUSTED_PROXY_HOPS")).isEqualTo("1");
        assertThat(serverProperties.getForwardHeadersStrategy())
                .isEqualTo(ServerProperties.ForwardHeadersStrategy.FRAMEWORK);
        assertThat(context.getEnvironment().getProperty("app.auth.login-trusted-proxy-hops")).isEqualTo("1");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void springForwardedHeaderFilterIsRegisteredAheadOfTheLoginFilter() {
        List<FilterRegistrationBean> forwarded = context.getBeansOfType(FilterRegistrationBean.class).values()
                .stream().filter(registration -> registration.getFilter() instanceof ForwardedHeaderFilter)
                .toList();

        assertThat(forwarded).hasSize(1);
        FilterRegistrationBean registration = forwarded.getFirst();
        assertThat(registration.isEnabled()).isTrue();
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE)
                .isLessThan(OrderUtils.getOrder(LoginRateLimitFilter.class, Ordered.LOWEST_PRECEDENCE));
    }

    @Test
    void springWrapperAndResolverSeeDifferentAddressesOnTheSameRealRequest() {
        String token = accessToken();
        for (String spoofed : new String[]{"198.51.100.66", "198.51.100.67, 198.51.100.68", "unknown"}) {
            HttpHeaders headers = forwardedFor(spoofed + ", 203.0.113.99");
            headers.setBearerAuth(token);
            ResponseEntity<JsonNode> probe = http.exchange("/api/v1/test-probe/forwarding", HttpMethod.GET,
                    new HttpEntity<>(headers), JsonNode.class);

            assertStatus(probe, 200);
            // ForwardedHeaderFilter is active in the real chain: it reports the client-supplied value...
            assertThat(body(probe).path("remoteAddr").asText()).isEqualTo(spoofed.split(",")[0].trim());
            assertThat(body(probe).path("forwardedForVisible").asBoolean()).isFalse();
            // ...while the resolver always lands on the proxy-written entry.
            assertThat(body(probe).path("resolved").asText()).isEqualTo("203.0.113.99");
        }
    }

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
    void failuresFromManyRealClientAddressesReachTheAccountLimit() {
        for (int client = 1; client <= 11; client++) {
            assertStatus(login("distributed.target", forwardedFor("203.0.113." + (100 + client))), 401);
        }
        ResponseEntity<JsonNode> blocked = login("distributed.target", forwardedFor("203.0.113.120"));
        assertStatus(blocked, 429);
        assertThat(body(blocked).path("code").asText()).isEqualTo("LOGIN_RATE_LIMITED");
        assertStatus(login("distributed.other", forwardedFor("203.0.113.121")), 401);
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

    private String accessToken() {
        HttpHeaders headers = forwardedFor("203.0.113.98");
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> login = http.postForEntity("/api/v1/auth/login", new HttpEntity<>(
                Map.of("username", TEST_USERNAME, "password", TEST_PASSWORD), headers), JsonNode.class);
        assertStatus(login, 200);
        return body(login).path("accessToken").asText();
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

    @SuppressWarnings("unchecked")
    private static Map<String, String> renderEnvironment() {
        try (Reader reader = Files.newBufferedReader(RENDER_BLUEPRINT)) {
            Map<String, Object> blueprint = new Yaml().load(reader);
            Map<String, Object> service = ((List<Map<String, Object>>) blueprint.get("services")).getFirst();
            Map<String, String> environment = new HashMap<>();
            for (Map<String, Object> variable : (List<Map<String, Object>>) service.get("envVars")) {
                if (variable.get("value") != null) {
                    environment.put(String.valueOf(variable.get("key")), String.valueOf(variable.get("value")));
                }
            }
            return environment;
        } catch (IOException exception) {
            throw new UncheckedIOException("render.yaml must be readable from the backend module", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now());
        }

        @Bean
        ForwardingProbe forwardingProbe(LoginClientAddressResolver resolver) {
            return new ForwardingProbe(resolver);
        }
    }

    /** Test-only endpoint exposing what each layer sees for the same request. */
    @RestController
    static class ForwardingProbe {

        private final LoginClientAddressResolver resolver;

        ForwardingProbe(LoginClientAddressResolver resolver) {
            this.resolver = resolver;
        }

        @GetMapping("/api/v1/test-probe/forwarding")
        Map<String, Object> forwarding(HttpServletRequest request) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("remoteAddr", request.getRemoteAddr());
            view.put("forwardedForVisible", request.getHeader("X-Forwarded-For") != null);
            view.put("resolved", resolver.resolve(request));
            return view;
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
