package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.auth.login-max-attempts=100")
class StageKFlowIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void healthcheckIsPublicMinimalAndNotCached() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void userLifecycleHashesPasswordsUsesVersionsAndRevokesSessions() throws Exception {
        String administratorToken = login(TEST_USERNAME, TEST_PASSWORD);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "stage.k." + suffix;
        String password = "initial-password-" + suffix;

        JsonNode created = body(mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Usuário K", "username", username, "password", password))))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn());

        String stored = jdbcTemplate.queryForObject(
                "select password_hash from app_user where id = ?::uuid", String.class, created.path("id").asText());
        assertThat(stored).startsWith("$argon2id$").doesNotContain(password);

        String secondToken = login(username, password);
        JsonNode updated = body(mockMvc.perform(patch("/api/v1/users/{id}", created.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 0, "name", "Usuário K Atualizado",
                                "newPassword", "updated-password-" + suffix))))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(patch("/api/v1/users/{id}", created.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(secondToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", 0, "name", "Versão obsoleta"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        mockMvc.perform(post("/api/v1/users/{id}/deactivate", created.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(administratorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", updated.path("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void auditIsAuthenticatedFilteredReadOnlyAndSanitizesLegacyChanges() throws Exception {
        mockMvc.perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
        String token = login(TEST_USERNAME, TEST_PASSWORD);
        UUID actor = jdbcTemplate.queryForObject(
                "select id from app_user where username = ?", UUID.class, TEST_USERNAME);
        UUID eventId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into audit_log
                    (id, occurred_at, actor_user_id, action, entity_type, entity_id,
                     entity_reference, summary, changes, request_id)
                values (?, now(), ?, 'UPDATED', 'USER', ?, 'legacy.user', 'Evento legado',
                    cast(? as jsonb), ?)
                """, eventId, actor, entityId,
                "{\"name\":\"ok\",\"passwordHash\":\"secret\",\"nested\":{\"token\":\"secret\",\"safe\":true}}",
                UUID.randomUUID());

        mockMvc.perform(get("/api/v1/audit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("entityType", "USER")
                        .param("action", "UPDATED")
                        .param("sort", "occurredAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entityType").value("USER"));

        MvcResult detail = mockMvc.perform(get("/api/v1/audit/{id}", eventId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.name").value("ok"))
                .andExpect(jsonPath("$.changes.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.changes.nested.token").doesNotExist())
                .andReturn();
        assertThat(detail.getResponse().getContentAsString()).doesNotContain("secret");

        mockMvc.perform(post("/api/v1/audit").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(get("/api/v1/audit/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_EVENT_NOT_FOUND"));
    }

    private String login(String username, String password) throws Exception {
        return body(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn()).path("accessToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
