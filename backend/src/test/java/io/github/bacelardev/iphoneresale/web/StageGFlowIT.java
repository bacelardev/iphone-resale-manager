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

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StageGFlowIT extends PostgresIntegrationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void catalogsEnforceCaseInsensitiveUniquenessVersionAndSortAllowlist() throws Exception {
        String token = login();
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        JsonNode model = json(mockMvc.perform(post("/api/v1/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "MODEL_" + suffix,
                                "name", "Modelo " + suffix,
                                "displayOrder", 10))))
                .andExpect(status().isCreated()).andReturn());

        mockMvc.perform(post("/api/v1/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "ANOTHER_" + suffix,
                                "name", ("Modelo " + suffix).toLowerCase(),
                                "displayOrder", 11))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_NAME_ALREADY_EXISTS"));

        mockMvc.perform(patch("/api/v1/models/{id}", model.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expectedVersion", model.path("version").asLong() + 1,
                                "name", "Modelo alterado " + suffix,
                                "displayOrder", 12))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        mockMvc.perform(get("/api/v1/models?sort=passwordHash,desc")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void initializationOperationalAndInitialInventoryFlowsPreserveLedgerBoundary() throws Exception {
        String token = login();
        JsonNode model = createModel(token, "IPHONE_STAGE_G", "iPhone Stage G");
        JsonNode color = createColor(token, "BLACK_STAGE_G", "Preto Stage G");
        Instant cutoff = Instant.now().minusSeconds(60);

        JsonNode initialization = json(mockMvc.perform(post("/api/v1/business-initialization/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("cutoffAt", cutoff))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andReturn());

        mockMvc.perform(post("/api/v1/business-initialization/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("cutoffAt", cutoff))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_INITIALIZATION_ALREADY_STARTED"));

        JsonNode operational = createDevice(token, model, color, "/api/v1/devices",
                Instant.now().minusSeconds(30), "PENDENTE_MANUTENCAO");
        UUID operationalId = UUID.fromString(operational.path("id").asText());
        assertThat(activePurchaseCount(operationalId)).isEqualTo(1);

        JsonNode imported = createDevice(token, model, color, "/api/v1/devices/initial-import",
                cutoff.minusSeconds(3600), "DISPONIVEL_VENDA");
        UUID importedId = UUID.fromString(imported.path("id").asText());
        assertThat(activePurchaseCount(importedId)).isZero();
        assertThat(imported.path("registrationOrigin").asText()).isEqualTo("INITIAL_IMPORT");

        mockMvc.perform(patch("/api/v1/business-initialization")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expectedVersion", initialization.path("version").asLong(),
                                "cutoffAt", cutoff.minusSeconds(10)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INITIALIZATION_CUTOFF_LOCKED"));

        mockMvc.perform(get("/api/v1/business-initialization/preview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialDeviceCount").value(1))
                .andExpect(jsonPath("$.maintenanceCapital").value(0.0));

        JsonNode available = json(mockMvc.perform(post(
                                "/api/v1/devices/{id}/mark-available", operationalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expectedVersion", operational.path("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPONIVEL_VENDA"))
                .andReturn());

        JsonNode added = json(mockMvc.perform(multipart("/api/v1/devices/{id}/photos", operationalId)
                        .file(photo("file", "extra.png"))
                        .param("position", "3")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn());
        String signedUrl = added.path("url").asText();
        mockMvc.perform(get(URI.create(signedUrl)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/devices/{id}/photos/{photoId}",
                        operationalId, added.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/devices/{id}/archive", operationalId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "expectedVersion", available.path("version").asLong(),
                                "reason", "Encerramento do cadastro de teste."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
        assertThat(activePurchaseCount(operationalId)).isZero();
    }

    @Test
    void photoValidationUsesContentSignatureAndMinimumIsProtected() throws Exception {
        String token = login();
        JsonNode model = createModel(token, "PHOTO_MODEL", "Modelo fotos");
        JsonNode color = createColor(token, "PHOTO_COLOR", "Cor fotos");
        JsonNode device = createDevice(token, model, color, "/api/v1/devices",
                Instant.now().minusSeconds(30), "DISPONIVEL_VENDA");

        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.png", "image/png", "not a png".getBytes());
        mockMvc.perform(multipart("/api/v1/devices/{id}/photos", device.path("id").asText())
                        .file(fake).param("position", "3")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE_TYPE"));

        String firstPhotoId = device.path("photos").get(0).path("id").asText();
        mockMvc.perform(delete("/api/v1/devices/{id}/photos/{photoId}",
                        device.path("id").asText(), firstPhotoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PHOTO_MINIMUM_VIOLATION"));
    }

    private JsonNode createModel(String token, String code, String name) throws Exception {
        return json(mockMvc.perform(post("/api/v1/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", code, "name", name, "displayOrder", 1))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createColor(String token, String code, String name) throws Exception {
        return json(mockMvc.perform(post("/api/v1/colors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "name", name))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createDevice(
            String token,
            JsonNode model,
            JsonNode color,
            String path,
            Instant purchasedAt,
            String status
    ) throws Exception {
        MockMultipartFile device = new MockMultipartFile(
                "device", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(Map.of(
                        "modelId", model.path("id").asText(),
                        "colorId", color.path("id").asText(),
                        "storageGb", 256,
                        "purchasePrice", 2500.00,
                        "purchasedAt", purchasedAt,
                        "faceIdWorking", true,
                        "originalScreen", true,
                        "originalBattery", true,
                        "batteryHealthPercent", 0,
                        "initialStatus", status
                )));
        return json(mockMvc.perform(multipart(path)
                        .file(device)
                        .file(photo("photos", "front.png"))
                        .file(photo("photos", "back.png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batteryHealthPercent").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andReturn());
    }

    private static MockMultipartFile photo(String name, String filename) {
        return new MockMultipartFile(name, filename, "image/png", PNG);
    }

    private int activePurchaseCount(UUID deviceId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                  from financial_transaction original
                 where original.type = 'DEVICE_PURCHASE'
                   and original.device_id = ?
                   and not exists (
                       select 1 from financial_transaction reversal
                        where reversal.reversal_of_id = original.id
                   )
                """, Integer.class, deviceId);
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "password", TEST_PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json(result).path("accessToken").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
