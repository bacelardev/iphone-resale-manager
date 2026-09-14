package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StageJClosingAdjustmentsIT extends PostgresIntegrationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetStageJData() {
        jdbcTemplate.execute("""
                truncate table owner_capital_opening, audit_log, financial_transaction,
                    sale, maintenance_item, maintenance, device_photo, device,
                    part_catalog, device_color, iphone_model, business_initialization
                restart identity cascade
                """);
    }

    @Test
    void openingBalanceValidatesCutoffAuditsAndIsReusedByCompletion() throws Exception {
        String token = login();
        Instant cutoff = Instant.now().minusSeconds(3600);
        JsonNode initialization = start(token, cutoff);

        mockMvc.perform(post("/api/v1/financial/opening-balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "amount", 0,
                                "occurredAt", cutoff,
                                "description", "Valor inválido."))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/financial/opening-balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "amount", 4000,
                                "occurredAt", cutoff.plusSeconds(1),
                                "description", "Data divergente."))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPENING_BALANCE_CUTOFF_MISMATCH"));

        JsonNode opening = create(token, "/api/v1/financial/opening-balance", Map.of(
                "amount", new BigDecimal("4000.00"),
                "occurredAt", cutoff,
                "description", "Saldo inicial conferido."));
        assertThat(opening.path("type").asText()).isEqualTo("OPENING_BALANCE");
        assertThat(opening.path("direction").asText()).isEqualTo("INFLOW");
        assertThat(opening.path("description").asText()).isEqualTo("Saldo inicial conferido.");

        mockMvc.perform(patch("/api/v1/business-initialization")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedVersion", initialization.path("version").asLong(),
                                "cutoffAt", cutoff.minusSeconds(1)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INITIALIZATION_CUTOFF_LOCKED"));

        mockMvc.perform(post("/api/v1/business-initialization/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedVersion", initialization.path("version").asLong(),
                                "declaredCashBalance", 5000,
                                "ownerCapitalOpenings", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("INITIALIZATION_OPENING_BALANCE_MISMATCH"));

        JsonNode completed = complete(token, initialization, new BigDecimal("4000.00"));
        assertThat(completed.path("openingBalanceTransactionId").asText())
                .isEqualTo(opening.path("id").asText());
        assertThat(count("financial_transaction",
                "type = 'OPENING_BALANCE'")).isOne();
        assertThat(count("audit_log",
                "action = 'FINANCIAL_TRANSACTION_CREATED' and entity_id = '"
                        + opening.path("id").asText() + "'::uuid")).isOne();
    }

    @Test
    void concurrentOpeningBalancesCreateExactlyOneTransactionAndAudit() throws Exception {
        String token = login();
        Instant cutoff = Instant.now().minusSeconds(3600);
        start(token, cutoff);
        String request = json(Map.of(
                "amount", 3000,
                "occurredAt", cutoff,
                "description", "Saldo inicial concorrente."));

        Callable<Integer> register = () -> mockMvc.perform(
                        post("/api/v1/financial/opening-balance")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andReturn().getResponse().getStatus();

        assertThat(race(register, register)).containsExactlyInAnyOrder(201, 409);
        assertThat(count("financial_transaction",
                "type = 'OPENING_BALANCE'")).isOne();
        assertThat(count("audit_log",
                "action = 'FINANCIAL_TRANSACTION_CREATED' "
                        + "and entity_reference = 'OPENING_BALANCE'")).isOne();
    }

    @Test
    void completionWithZeroCashCreatesNoOpeningBalance() throws Exception {
        String token = login();
        JsonNode initialization = start(token, Instant.now().minusSeconds(3600));

        mockMvc.perform(post("/api/v1/business-initialization/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedVersion", initialization.path("version").asLong(),
                                "declaredCashBalance", 0,
                                "ownerCapitalOpenings", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.declaredCashBalance").value(0.00))
                .andExpect(jsonPath("$.openingBalanceTransactionId").value(nullValue()));

        assertThat(count("financial_transaction",
                "type = 'OPENING_BALANCE'")).isZero();
    }

    @Test
    void concurrentCompletionsAllowExactlyOneSuccess() throws Exception {
        String token = login();
        JsonNode initialization = start(token, Instant.now().minusSeconds(3600));
        String request = json(Map.of(
                "expectedVersion", initialization.path("version").asLong(),
                "declaredCashBalance", 0,
                "ownerCapitalOpenings", List.of()));

        Callable<Integer> complete = () -> mockMvc.perform(
                        post("/api/v1/business-initialization/complete")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request))
                .andReturn().getResponse().getStatus();

        assertThat(race(complete, complete)).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from business_initialization where status = 'COMPLETED'",
                Integer.class)).isOne();
        assertThat(count("financial_transaction",
                "type = 'OPENING_BALANCE'")).isZero();
    }

    @Test
    void ownerCanDifferFromCreatorAndCashCanBeNegativeWithFilteredLedger() throws Exception {
        String token = login();
        Instant cutoff = Instant.now().minusSeconds(3600);
        JsonNode initialization = start(token, cutoff);
        complete(token, initialization, BigDecimal.ZERO);

        UUID actorId = userId(TEST_USERNAME);
        UUID ownerId = createSecondOwner(actorId);

        JsonNode contribution = create(token, "/api/v1/financial/contributions", Map.of(
                "ownerUserId", ownerId,
                "amount", 1000,
                "occurredAt", cutoff.plusSeconds(60),
                "description", "Aporte pertencente a outro sócio."));
        assertThat(contribution.path("ownerUser").path("id").asText())
                .isEqualTo(ownerId.toString());
        assertThat(contribution.path("createdBy").path("id").asText())
                .isEqualTo(actorId.toString());
        assertThat(ownerId).isNotEqualTo(actorId);

        create(token, "/api/v1/financial/withdrawals", Map.of(
                "ownerUserId", ownerId,
                "amount", 5000,
                "occurredAt", cutoff.plusSeconds(120),
                "description", "Retirada que deixa o caixa negativo."));
        JsonNode adjustment = create(token, "/api/v1/financial/adjustments", Map.of(
                "direction", "OUTFLOW",
                "amount", 250,
                "occurredAt", cutoff.plusSeconds(180),
                "description", "Ajuste manual de saída."));
        assertThat(adjustment.path("direction").asText()).isEqualTo("OUTFLOW");

        mockMvc.perform(get("/api/v1/financial/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", cutoff.toString())
                        .param("to", cutoff.plusSeconds(600).toString())
                        .param("type", "OWNER_WITHDRAWAL", "MANUAL_ADJUSTMENT")
                        .param("direction", "OUTFLOW")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "amount,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("OWNER_WITHDRAWAL"))
                .andExpect(jsonPath("$.content[0].amount").value(5000.00));

        mockMvc.perform(get("/api/v1/financial/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", cutoff.toString())
                        .param("to", cutoff.plusSeconds(600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closingBalance").value(-4250.00));

        assertThat(count("audit_log",
                "action = 'FINANCIAL_TRANSACTION_CREATED' "
                        + "and entity_type = 'FINANCIAL_TRANSACTION'")).isEqualTo(3);
    }

    @Test
    void manualReversalRejectsOperationalTransaction() throws Exception {
        String token = login();
        Instant cutoff = Instant.now().minusSeconds(3600);
        complete(token, start(token, cutoff), BigDecimal.ZERO);
        JsonNode device = createDevice(token, "/api/v1/devices",
                cutoff.plusSeconds(60), "1800.00");
        UUID transactionId = jdbcTemplate.queryForObject("""
                select id from financial_transaction
                 where device_id = ? and type = 'DEVICE_PURCHASE'
                """, UUID.class, UUID.fromString(device.path("id").asText()));

        mockMvc.perform(post("/api/v1/financial/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "reversalOfTransactionId", transactionId,
                                "occurredAt", cutoff.plusSeconds(120),
                                "description", "Estorno operacional indevido."))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATIONAL_REVERSAL_NOT_ALLOWED"));
    }

    @Test
    void defaultSummaryUsesBahiaPeriodAndExplicitNullMargin() throws Exception {
        String token = login();
        JsonNode summary = body(mockMvc.perform(get("/api/v1/financial/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.businessTimezone").value("America/Bahia"))
                .andExpect(jsonPath("$.from").doesNotExist())
                .andExpect(jsonPath("$.to").doesNotExist())
                .andExpect(jsonPath("$.marginPercent").value(nullValue()))
                .andReturn());

        ZoneId bahia = ZoneId.of("America/Bahia");
        Instant from = Instant.parse(summary.path("period").path("from").asText());
        Instant to = Instant.parse(summary.path("period").path("to").asText());
        assertThat(from.atZone(bahia).getDayOfMonth()).isOne();
        assertThat(from.atZone(bahia).toLocalTime().toString()).isEqualTo("00:00");
        assertThat(to).isEqualTo(from.atZone(bahia).plusMonths(1).toInstant());
        assertThat(summary.path("marginPercent").isNull()).isTrue();
    }

    @Test
    void summarySeparatesOperationalEconomicsHistoricalStockAndInactiveSales() throws Exception {
        String token = login();
        Instant cutoff = Instant.now().minusSeconds(3600);
        JsonNode initialization = start(token, cutoff);

        JsonNode imported = createDevice(token, "/api/v1/devices/initial-import",
                cutoff.minusSeconds(600), "1000.00");
        JsonNode historicalPart = createPart(token);
        registerMaintenance(token, imported, historicalPart, cutoff.minusSeconds(300),
                "100.00", true);

        complete(token, initialization, BigDecimal.ZERO);

        JsonNode soldDevice = createDevice(token, "/api/v1/devices",
                cutoff.plusSeconds(10), "2000.00");
        JsonNode salePart = createPart(token);
        registerMaintenance(token, soldDevice, salePart, cutoff.plusSeconds(20),
                "200.00", false);
        JsonNode soldAvailable = markAvailable(token, soldDevice);
        registerSale(token, soldAvailable, "3000.00", cutoff.plusSeconds(30));

        JsonNode archived = createDevice(token, "/api/v1/devices",
                cutoff.plusSeconds(40), "1500.00");
        archive(token, archived);

        JsonNode stocked = createDevice(token, "/api/v1/devices",
                cutoff.plusSeconds(50), "1200.00");
        JsonNode stockPart = createPart(token);
        registerMaintenance(token, stocked, stockPart, cutoff.plusSeconds(60),
                "50.00", false);

        JsonNode cancelledDevice = createDevice(token, "/api/v1/devices",
                cutoff.plusSeconds(70), "900.00");
        JsonNode cancelledAvailable = markAvailable(token, cancelledDevice);
        JsonNode cancelledSale = registerSale(token, cancelledAvailable,
                "1300.00", cutoff.plusSeconds(80));
        cancelSale(token, device(token, cancelledDevice.path("id").asText()), cancelledSale);

        JsonNode summary = body(mockMvc.perform(get("/api/v1/financial/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", cutoff.toString())
                        .param("to", Instant.now().plusSeconds(600).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(3000.00))
                .andExpect(jsonPath("$.devicePurchaseCost").value(4100.00))
                .andExpect(jsonPath("$.maintenanceCost").value(250.00))
                .andExpect(jsonPath("$.profit").value(800.00))
                .andExpect(jsonPath("$.marginPercent").value(26.6667))
                .andExpect(jsonPath("$.stockCapital").value(3250.00))
                .andReturn());

        assertThat(summary.path("marginPercent").decimalValue().scale()).isEqualTo(4);
        assertThat(count("financial_transaction",
                "device_id = '" + imported.path("id").asText()
                        + "'::uuid and type = 'DEVICE_PURCHASE'")).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                select status from sale where id = ?
                """, String.class, UUID.fromString(cancelledSale.path("id").asText())))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("""
                select status from device where id = ?
                """, String.class, UUID.fromString(soldDevice.path("id").asText())))
                .isEqualTo("VENDIDO");
        assertThat(jdbcTemplate.queryForObject("""
                select archived_at is not null from device where id = ?
                """, Boolean.class, UUID.fromString(archived.path("id").asText())))
                .isTrue();
    }

    private JsonNode start(String token, Instant cutoff) throws Exception {
        return body(mockMvc.perform(post("/api/v1/business-initialization/start")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("cutoffAt", cutoff))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode complete(
            String token,
            JsonNode initialization,
            BigDecimal declaredCash
    ) throws Exception {
        return body(mockMvc.perform(post("/api/v1/business-initialization/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedVersion", initialization.path("version").asLong(),
                                "declaredCashBalance", declaredCash,
                                "ownerCapitalOpenings", List.of()))))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode create(String token, String path, Object input) throws Exception {
        return body(mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(input)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createDevice(
            String token,
            String path,
            Instant purchasedAt,
            String price
    ) throws Exception {
        String suffix = suffix();
        JsonNode model = body(mockMvc.perform(post("/api/v1/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "J_MODEL_" + suffix,
                                "name", "Modelo J " + suffix,
                                "displayOrder", 1))))
                .andExpect(status().isCreated()).andReturn());
        JsonNode color = body(mockMvc.perform(post("/api/v1/colors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "J_COLOR_" + suffix,
                                "name", "Cor J " + suffix))))
                .andExpect(status().isCreated()).andReturn());
        MockMultipartFile metadata = new MockMultipartFile(
                "device",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(Map.of(
                        "modelId", model.path("id").asText(),
                        "colorId", color.path("id").asText(),
                        "storageGb", 256,
                        "purchasePrice", new BigDecimal(price),
                        "purchasedAt", purchasedAt,
                        "faceIdWorking", true,
                        "originalScreen", true,
                        "originalBattery", true,
                        "batteryHealthPercent", 90,
                        "initialStatus", "PENDENTE_MANUTENCAO"
                ))
        );
        return body(mockMvc.perform(multipart(path)
                        .file(metadata)
                        .file(photo("front.png"))
                        .file(photo("back.png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createPart(String token) throws Exception {
        String suffix = suffix();
        return body(mockMvc.perform(post("/api/v1/parts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "code", "J_PART_" + suffix,
                                "name", "Peça J " + suffix))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode registerMaintenance(
            String token,
            JsonNode device,
            JsonNode part,
            Instant performedAt,
            String cost,
            boolean historical
    ) throws Exception {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("partId", part.path("id").asText());
        item.put("details", null);
        item.put("cost", new BigDecimal(cost));
        return body(mockMvc.perform(post(
                                "/api/v1/devices/{id}/maintenances"
                                        + (historical ? "/initial-import" : ""),
                                device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "performedAt", performedAt,
                                "items", List.of(item)))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode markAvailable(String token, JsonNode device) throws Exception {
        return body(mockMvc.perform(post("/api/v1/devices/{id}/mark-available",
                                device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedVersion", device.path("version").asLong()))))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode registerSale(
            String token,
            JsonNode device,
            String price,
            Instant soldAt
    ) throws Exception {
        return body(mockMvc.perform(post("/api/v1/devices/{id}/sale",
                                device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "deviceVersion", device.path("version").asLong(),
                                "salePrice", new BigDecimal(price),
                                "soldAt", soldAt))))
                .andExpect(status().isCreated()).andReturn());
    }

    private void cancelSale(String token, JsonNode device, JsonNode sale) throws Exception {
        mockMvc.perform(post("/api/v1/devices/{id}/sale/cancel",
                                device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "deviceVersion", device.path("version").asLong(),
                                "saleVersion", sale.path("version").asLong(),
                                "reason", "Venda cancelada para validar o resumo."))))
                .andExpect(status().isOk());
    }

    private void archive(String token, JsonNode device) throws Exception {
        mockMvc.perform(post("/api/v1/devices/{id}/archive",
                                device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedVersion", device.path("version").asLong(),
                                "reason", "Aparelho arquivado para validar o resumo."))))
                .andExpect(status().isOk());
    }

    private JsonNode device(String token, String id) throws Exception {
        return body(mockMvc.perform(get("/api/v1/devices/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn());
    }

    private UUID createSecondOwner(UUID actorId) {
        String username = "owner." + suffix().toLowerCase();
        return jdbcTemplate.queryForObject("""
                insert into app_user (
                    name, username, password_hash, role, active, created_by, updated_by
                )
                select 'Outro Sócio', ?, password_hash, 'SOCIO', true, ?, ?
                  from app_user where username = ?
                returning id
                """, UUID.class, username, actorId, actorId, TEST_USERNAME);
    }

    private UUID userId(String username) {
        return jdbcTemplate.queryForObject(
                "select id from app_user where username = ?",
                UUID.class,
                username
        );
    }

    private int count(String table, String predicate) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + predicate,
                Integer.class
        );
    }

    private List<Integer> race(
            Callable<Integer> first,
            Callable<Integer> second
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (Callable<Integer> operation : List.of(first, second)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Race start timeout");
                    }
                    return operation.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS)
            );
        }
    }

    private String login() throws Exception {
        return body(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", TEST_USERNAME,
                                "password", TEST_PASSWORD))))
                .andExpect(status().isOk()).andReturn())
                .path("accessToken").asText();
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
