package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StageHFlowIT extends PostgresIntegrationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void partsSupportCrudSearchVersionAndHistory() throws Exception {
        String token = login();
        String suffix = suffix();
        JsonNode part = createPart(token, "screen_" + suffix, "Tela " + suffix);
        assertThat(part.path("code").asText()).isEqualTo("SCREEN_" + suffix);

        mockMvc.perform(post("/api/v1/parts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "DISPLAY_" + suffix,
                                "name", ("Tela " + suffix).toLowerCase()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_NAME_ALREADY_EXISTS"));

        JsonNode renamed = body(mockMvc.perform(patch("/api/v1/parts/{id}", part.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", part.path("version").asLong(),
                                "name", "Tela OLED " + suffix))))
                .andExpect(status().isOk()).andReturn());

        mockMvc.perform(post("/api/v1/parts/{id}/deactivate", part.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", renamed.path("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/parts?search=OLED&active=false")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("SCREEN_" + suffix));
    }

    @Test
    void operationalMaintenanceCreatesLedgerAndCancellationCreatesReversal() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode device = createDevice(token, "/api/v1/devices", cutoff.plusSeconds(1));
        JsonNode part = createPart(token, "BATTERY_" + suffix(), "Bateria " + suffix());

        JsonNode maintenance = register(token, device, cutoff.plusSeconds(2), List.of(
                item(part, null, 250.00), item(part, "Limpeza técnica", 0.00)), false);
        UUID id = UUID.fromString(maintenance.path("id").asText());
        assertThat(maintenance.path("total").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(activeLedger(id, "MAINTENANCE")).isEqualTo(1);

        mockMvc.perform(post("/api/v1/devices/{deviceId}/maintenances/{id}/cancel",
                                device.path("id").asText(), id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Versão obrigatória ausente."))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/devices/{deviceId}/maintenances/{id}/cancel",
                                device.path("id").asText(), id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", maintenance.path("version").asLong(),
                                "reason", "Lançamento duplicado."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(activeLedger(id, "MAINTENANCE")).isZero();
        assertThat(transactionCount(id, "MAINTENANCE_REVERSAL")).isEqualTo(1);

        mockMvc.perform(post("/api/v1/devices/{deviceId}/maintenances/{id}/cancel",
                                device.path("id").asText(), id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", maintenance.path("version").asLong() + 1,
                                "reason", "Tentativa repetida."))))
                .andExpect(status().isConflict());
    }

    @Test
    void historicalMaintenanceHonorsCutoverWithoutLedgerAndUpdatesPreview() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode imported = createDevice(token, "/api/v1/devices/initial-import",
                cutoff.minusSeconds(3600));
        JsonNode operational = createDevice(token, "/api/v1/devices", cutoff.plusSeconds(1));
        JsonNode part = createPart(token, "CAMERA_" + suffix(), "Câmera " + suffix());

        JsonNode historical = register(token, imported, cutoff.minusSeconds(30),
                List.of(item(part, null, 175.50)), true);
        UUID id = UUID.fromString(historical.path("id").asText());
        assertThat(transactionCount(id, "MAINTENANCE")).isZero();

        mockMvc.perform(get("/api/v1/business-initialization/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maintenanceCapital").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(175.5)));

        mockMvc.perform(post("/api/v1/devices/{id}/maintenances", imported.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(cutoff, List.of(item(part, null, 10.00))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAINTENANCE_REQUIRES_OPERATIONAL_PERIOD"));

        mockMvc.perform(post("/api/v1/devices/{id}/maintenances/initial-import",
                                operational.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(cutoff.minusSeconds(1),
                                List.of(item(part, null, 10.00))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(
                        "INITIAL_MAINTENANCE_REQUIRES_IMPORTED_DEVICE"));
    }

    @Test
    void otherRequiresDetailsAndArchiveCancelsActiveMaintenancesAtomically() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode device = createDevice(token, "/api/v1/devices", cutoff.plusSeconds(1));
        JsonNode other = createPart(token, "OTHER", "Outro " + suffix());

        mockMvc.perform(post("/api/v1/devices/{id}/maintenances", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(cutoff.plusSeconds(2),
                                List.of(item(other, null, 80.00))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DETAILS_REQUIRED_FOR_OTHER"));

        JsonNode maintenance = register(token, device, cutoff.plusSeconds(2),
                List.of(item(other, "Microssolda na placa", 80.00)), false);
        UUID maintenanceId = UUID.fromString(maintenance.path("id").asText());

        mockMvc.perform(post("/api/v1/devices/{id}/archive", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", device.path("version").asLong(),
                                "reason", "Aparelho retirado da operação."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true))
                .andExpect(jsonPath("$.maintenanceTotal").value(0.0));

        assertThat(activeLedger(maintenanceId, "MAINTENANCE")).isZero();
        assertThat(transactionCount(maintenanceId, "MAINTENANCE_REVERSAL")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_log
                 where entity_id = ? and action = 'MAINTENANCE_CANCELLED'
                """, Integer.class, maintenanceId)).isZero();
    }

    private Instant ensureInitialization(String token) throws Exception {
        JsonNode current = body(mockMvc.perform(get("/api/v1/business-initialization")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn());
        if (!"NOT_STARTED".equals(current.path("status").asText())) {
            return Instant.parse(current.path("cutoffAt").asText());
        }
        Instant cutoff = Instant.now().minusSeconds(3600);
        return Instant.parse(body(mockMvc.perform(post("/api/v1/business-initialization/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cutoffAt", cutoff))))
                .andExpect(status().isCreated()).andReturn()).path("cutoffAt").asText());
    }

    private JsonNode createPart(String token, String code, String name) throws Exception {
        return body(mockMvc.perform(post("/api/v1/parts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", code, "name", name))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createDevice(String token, String path, Instant purchasedAt) throws Exception {
        String suffix = suffix();
        JsonNode model = body(mockMvc.perform(post("/api/v1/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "H_MODEL_" + suffix,
                                "name", "Modelo H " + suffix, "displayOrder", 1))))
                .andExpect(status().isCreated()).andReturn());
        JsonNode color = body(mockMvc.perform(post("/api/v1/colors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "H_COLOR_" + suffix,
                                "name", "Cor H " + suffix))))
                .andExpect(status().isCreated()).andReturn());
        MockMultipartFile device = new MockMultipartFile("device", "",
                MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(Map.of(
                "modelId", model.path("id").asText(), "colorId", color.path("id").asText(),
                "storageGb", 256, "purchasePrice", 2500.00, "purchasedAt", purchasedAt,
                "faceIdWorking", true, "originalScreen", true, "originalBattery", true,
                "batteryHealthPercent", 90, "initialStatus", "PENDENTE_MANUTENCAO")));
        return body(mockMvc.perform(multipart(path).file(device)
                        .file(photo("front.png")).file(photo("back.png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode register(
            String token,
            JsonNode device,
            Instant performedAt,
            List<Map<String, Object>> items,
            boolean historical
    ) throws Exception {
        return body(mockMvc.perform(post("/api/v1/devices/{id}/maintenances" +
                                (historical ? "/initial-import" : ""), device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request(performedAt, items))))
                .andExpect(status().isCreated()).andReturn());
    }

    private static Map<String, Object> request(Instant performedAt, List<Map<String, Object>> items) {
        return Map.of("performedAt", performedAt, "items", items);
    }

    private static Map<String, Object> item(
            JsonNode part, String details, double cost
    ) {
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("partId", part.path("id").asText());
        item.put("details", details);
        item.put("cost", cost);
        return item;
    }

    private int activeLedger(UUID maintenanceId, String type) {
        return jdbcTemplate.queryForObject("""
                select count(*) from financial_transaction original
                 where original.maintenance_id = ? and original.type = ?
                   and not exists (select 1 from financial_transaction reversal
                                    where reversal.reversal_of_id = original.id)
                """, Integer.class, maintenanceId, type);
    }

    private int transactionCount(UUID maintenanceId, String type) {
        return jdbcTemplate.queryForObject("""
                select count(*) from financial_transaction
                 where maintenance_id = ? and type = ?
                """, Integer.class, maintenanceId, type);
    }

    private String login() throws Exception {
        return body(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", TEST_USERNAME, "password", TEST_PASSWORD))))
                .andExpect(status().isOk()).andReturn()).path("accessToken").asText();
    }

    private MockMultipartFile photo(String filename) {
        return new MockMultipartFile("photos", filename, "image/png", PNG);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
