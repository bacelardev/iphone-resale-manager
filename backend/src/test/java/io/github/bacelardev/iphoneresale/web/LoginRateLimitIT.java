package io.github.bacelardev.iphoneresale.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.auth.login-max-attempts=2")
class LoginRateLimitIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;

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
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
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

    private static RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
