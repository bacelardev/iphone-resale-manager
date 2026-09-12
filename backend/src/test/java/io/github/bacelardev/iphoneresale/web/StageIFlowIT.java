package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.FinancialTransactionJpaRepository;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StageIFlowIT extends PostgresIntegrationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private FinancialTransactionJpaRepository transactionRepository;

    @Test
    void registersSaleWithOfficialCalculationsLedgerAuditAndSoldStatus() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode device = availableDevice(token, cutoff.plusSeconds(10), "2500.00");
        JsonNode part = createPart(token);
        registerMaintenance(token, device, part, cutoff.plusSeconds(20), "250.00");

        JsonNode sale = registerSale(token, device, "3200.00", cutoff.plusSeconds(30));

        assertThat(sale.path("purchasePrice").decimalValue()).isEqualByComparingTo("2500.00");
        assertThat(sale.path("maintenanceTotal").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(sale.path("investmentTotal").decimalValue()).isEqualByComparingTo("2750.00");
        assertThat(sale.path("profit").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(sale.path("marginPercent").decimalValue()).isEqualByComparingTo("14.0625");
        assertThat(activeSaleLedger(UUID.fromString(sale.path("id").asText()))).isEqualTo(1);
        mockMvc.perform(get("/api/v1/devices/{id}", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VENDIDO"));
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from audit_log
                 where entity_id = ? and action = 'SALE_REGISTERED'
                """, Integer.class, UUID.fromString(sale.path("id").asText()))).isEqualTo(1);
    }

    @Test
    void validatesRequiredVersionPurchaseCutoffAndActiveMaintenanceChronology() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        Instant purchasedAt = cutoff.plusSeconds(100);
        JsonNode device = availableDevice(token, purchasedAt, "1800.00");

        mockMvc.perform(post("/api/v1/devices/{id}/sale", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("salePrice", 2200, "soldAt", purchasedAt.plusSeconds(10)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        saleFailure(token, device, purchasedAt.minusSeconds(1), "SALE_DATE_BEFORE_PURCHASE");
        saleFailure(token, device, cutoff, "SALE_DATE_BEFORE_PURCHASE");

        JsonNode part = createPart(token);
        registerMaintenance(token, device, part, purchasedAt.plusSeconds(30), "100.00");
        saleFailure(token, device, purchasedAt.plusSeconds(20),
                "SALE_DATE_BEFORE_ACTIVE_MAINTENANCE");
    }

    @Test
    void permitsLossCancelsWithReversalAndAllowsANewSale() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode device = availableDevice(token, cutoff.plusSeconds(200), "2500.00");
        JsonNode sale = registerSale(token, device, "2300.00", cutoff.plusSeconds(210));
        assertThat(sale.path("profit").decimalValue()).isEqualByComparingTo("-200.00");
        assertThat(sale.path("marginPercent").decimalValue()).isEqualByComparingTo("-8.6957");

        JsonNode soldDevice = device(token, device.path("id").asText());
        mockMvc.perform(post("/api/v1/devices/{id}/sale/cancel", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Campos de versão ausentes."))))
                .andExpect(status().isBadRequest());

        JsonNode cancelled = body(mockMvc.perform(post("/api/v1/devices/{id}/sale/cancel",
                                device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("saleVersion", sale.path("version").asLong(),
                                "deviceVersion", soldDevice.path("version").asLong(),
                                "reason", "Venda desfeita e valor devolvido."))))
                .andExpect(status().isOk()).andReturn());
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        UUID saleId = UUID.fromString(sale.path("id").asText());
        assertThat(activeSaleLedger(saleId)).isZero();
        assertThat(saleTransactionCount(saleId, "SALE_REVERSAL")).isEqualTo(1);

        mockMvc.perform(get("/api/v1/devices/{id}/sale", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SALE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/devices/{id}/sale/cancel", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("saleVersion", cancelled.path("version").asLong(),
                                "deviceVersion", device(token, device.path("id").asText())
                                        .path("version").asLong(),
                                "reason", "Tentativa repetida."))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALE_ALREADY_CANCELLED"));

        JsonNode available = device(token, device.path("id").asText());
        JsonNode replacement = registerSale(token, available, "2400.00", cutoff.plusSeconds(220));
        assertThat(replacement.path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void initialImportDeviceCanOnlyBeSoldAfterCutoff() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode imported = createDevice(token, "/api/v1/devices/initial-import",
                cutoff.minusSeconds(3600), "1500.00");
        imported = markAvailable(token, imported);

        mockMvc.perform(post("/api/v1/devices/{id}/sale", imported.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceVersion", imported.path("version").asLong(),
                                "salePrice", 2000, "soldAt", cutoff))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SALE_REQUIRES_OPERATIONAL_PERIOD"));

        JsonNode sale = registerSale(token, imported, "2000.00", cutoff.plusSeconds(1));
        assertThat(sale.path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void v5RejectsDirectSaleOutsideCutover() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode device = availableDevice(token, cutoff.plusSeconds(300), "1900.00");
        UUID userId = jdbcTemplate.queryForObject(
                "select id from app_user where username = ?", UUID.class, TEST_USERNAME);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into sale (device_id, sale_price, sold_at, responsible_user_id,
                                  created_by, updated_by)
                values (?, 2100.00, ?, ?, ?, ?)
                """, UUID.fromString(device.path("id").asText()), Timestamp.from(cutoff), userId, userId, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


    @Test @Order(1)
    void saleWithoutInitializationAllowsPurchaseBoundaryAndFutureDate() throws Exception {
        String token = login();
        assertThat(jdbcTemplate.queryForObject("select count(*) from business_initialization", Integer.class)).isZero();
        Instant future = Instant.now().plusSeconds(86400);
        JsonNode device = availableDevice(token, future, "1800.00");
        Instant persistedPurchaseDate = Instant.parse(device.path("purchasedAt").asText());
        JsonNode sale = registerSale(token, device, "1800.00", persistedPurchaseDate);
        assertThat(sale.path("profit").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(sale.path("marginPercent").decimalValue()).isEqualByComparingTo("0.0000");
        assertThat(sale.path("maintenanceTotal").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test @Order(2)
    void cutoffCannotMoveAcrossRegisteredOrCancelledSaleInServiceAndDatabase() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode device = availableDevice(token, cutoff.plusSeconds(1), "1800.00");
        JsonNode sale = registerSale(token, device, "2100.00", cutoff.plusSeconds(2));
        rejectCutoff(token, cutoff.plusSeconds(2));
        cancelSale(token, device(token, device.path("id").asText()), sale, "Correção");
        rejectCutoff(token, cutoff.plusSeconds(2));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update business_initialization set cutoff_at = ?", Timestamp.from(cutoff.plusSeconds(2))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("business cutoff conflicts with registered inventory, maintenance or sale");
    }

    @Test
    void rejectsInvalidMoneyVersionsUnknownPropertiesAndAnonymousRequests() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(900);
        JsonNode device = availableDevice(token, at, "1800.00");
        String route = "/api/v1/devices/" + device.path("id").asText() + "/sale";
        for (String price : List.of("0", "-1", "1.001", "1000000000000")) {
            mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                            "deviceVersion", device.path("version").asLong(), "salePrice", new BigDecimal(price), "soldAt", at))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String field : List.of("deviceVersion", "salePrice", "soldAt")) {
            java.util.HashMap<String, Object> input = new java.util.HashMap<>(Map.of(
                    "deviceVersion", 1, "salePrice", 2200, "soldAt", at));
            input.put(field, null);
            mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(json(input)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                        "deviceVersion", 99, "salePrice", 2200, "soldAt", at))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                        "deviceVersion", -1, "salePrice", 2200, "soldAt", at))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                        "deviceVersion", 1, "salePrice", 2200, "soldAt", at, "profit", 100))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(route)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(route + "/cancel").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("select count(*) from sale where device_id = ?", Integer.class,
                UUID.fromString(device.path("id").asText()))).isZero();
    }

    @Test
    void rejectsPendingArchivedMissingAndDuplicateSales() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(1000);
        JsonNode pending = createDevice(token, "/api/v1/devices", at, "1800.00");
        saleFailure(token, pending, at, "DEVICE_NOT_AVAILABLE_FOR_SALE");
        JsonNode archived = body(mockMvc.perform(post("/api/v1/devices/{id}/archive", pending.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("expectedVersion", pending.path("version").asLong(), "reason", "Cadastro inválido"))))
                .andExpect(status().isOk()).andReturn());
        saleFailure(token, archived, at, "DEVICE_ARCHIVED");
        mockMvc.perform(get("/api/v1/devices/{id}/sale", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"));
        JsonNode available = availableDevice(token, at, "1800.00");
        registerSale(token, available, "2000.00", at);
        JsonNode sold = device(token, available.path("id").asText());
        mockMvc.perform(post("/api/v1/devices/{id}/sale", sold.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("deviceVersion", sold.path("version").asLong(), "salePrice", 2300, "soldAt", at))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_ALREADY_EXISTS"));
    }

    @Test
    void cancelledFutureMaintenanceDoesNotAffectCostOrChronology() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(1100);
        JsonNode device = availableDevice(token, at, "1800.00");
        JsonNode part = createPart(token);
        registerMaintenance(token, device, part, at.plusSeconds(1), "150.00");
        registerMaintenance(token, device, part, at.plusSeconds(2), "100.00");
        JsonNode cancelled = registerMaintenance(token, device, part, at.plusSeconds(100), "999.00");
        mockMvc.perform(post("/api/v1/devices/{id}/maintenances/{maintenanceId}/cancel",
                        device.path("id").asText(), cancelled.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("expectedVersion", cancelled.path("version").asLong(), "reason", "Registro inválido"))))
                .andExpect(status().isOk());
        JsonNode sale = registerSale(token, device, "3200.00", at.plusSeconds(2));
        assertThat(sale.path("maintenanceTotal").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(sale.path("investmentTotal").decimalValue()).isEqualByComparingTo("2050.00");
        assertThat(sale.path("profit").decimalValue()).isEqualByComparingTo("1150.00");
        assertThat(sale.path("marginPercent").decimalValue()).isEqualByComparingTo("35.9375");
        String response = mockMvc.perform(get("/api/v1/devices/{id}/sale", device.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("35.9375").doesNotContain("storageKey", "passwordHash", "transactionId", "token");
    }

    @Test
    void activeSaleBlocksArchivePurchaseChangesAndMaintenanceMutations() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(1300);
        JsonNode device = availableDevice(token, at, "1800.00");
        JsonNode part = createPart(token);
        JsonNode maintenance = registerMaintenance(token, device, part, at, "100.00");
        JsonNode sale = registerSale(token, device, "2200.00", at);
        JsonNode sold = device(token, device.path("id").asText());
        mockMvc.perform(post("/api/v1/devices/{id}/archive", sold.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("expectedVersion", sold.path("version").asLong(), "reason", "Teste"))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DEVICE_HAS_ACTIVE_SALE"));
        for (Map<String, Object> change : List.of(Map.<String, Object>of("purchasePrice", 1700), Map.<String, Object>of("purchasedAt", at.minusSeconds(1)))) {
            java.util.HashMap<String, Object> input = new java.util.HashMap<>(change);
            input.put("expectedVersion", sold.path("version").asLong());
            mockMvc.perform(patch("/api/v1/devices/{id}", sold.path("id").asText())
                    .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DEVICE_PURCHASE_LOCKED_BY_SALE"));
        }
        mockMvc.perform(post("/api/v1/devices/{id}/maintenances", sold.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("performedAt", at, "items", List.of(Map.of("partId", part.path("id").asText(), "cost", 1))))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("DEVICE_ALREADY_SOLD"));
        mockMvc.perform(post("/api/v1/devices/{id}/maintenances/{mid}/cancel", sold.path("id").asText(), maintenance.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("expectedVersion", maintenance.path("version").asLong(), "reason", "Teste"))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(activeSaleLedger(UUID.fromString(sale.path("id").asText()))).isOne();
    }

    @Test
    void cancellationValidatesBothVersionsAndReasonBeforeAnyMutation() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(1400);
        JsonNode device = availableDevice(token, at, "1800.00");
        JsonNode sale = registerSale(token, device, "2200.00", at);
        JsonNode sold = device(token, device.path("id").asText());
        String route = "/api/v1/devices/" + device.path("id").asText() + "/sale/cancel";
        for (String field : List.of("deviceVersion", "saleVersion")) {
            for (Object invalid : java.util.Arrays.asList(null, -1L)) {
                java.util.HashMap<String, Object> input = new java.util.HashMap<>(Map.of(
                        "deviceVersion", sold.path("version").asLong(), "saleVersion", sale.path("version").asLong(), "reason", "Teste"));
                input.put(field, invalid);
                mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isBadRequest());
                input.remove(field);
                mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isBadRequest());
            }
            java.util.HashMap<String, Object> input = new java.util.HashMap<>(Map.of(
                    "deviceVersion", sold.path("version").asLong(), "saleVersion", sale.path("version").asLong(), "reason", "Teste"));
            input.put(field, 99L);
            mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(json(input)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
        }
        for (String reason : List.of("", "   ", "x".repeat(501))) {
            mockMvc.perform(post(route).header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of(
                            "deviceVersion", sold.path("version").asLong(), "saleVersion", sale.path("version").asLong(), "reason", reason))))
                    .andExpect(status().isBadRequest());
        }
        UUID saleId = UUID.fromString(sale.path("id").asText());
        assertThat(saleTransactionCount(saleId, "SALE_REVERSAL")).isZero();
        JsonNode cancelled = cancelSale(token, sold, sale, "x".repeat(500));
        assertThat(cancelled.path("cancellationReason").asText()).hasSize(500);
        assertThat(cancelled.path("cancelledBy").path("id").asText()).isEqualTo(sale.path("responsibleUser").path("id").asText());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from financial_transaction r join financial_transaction o on o.id=r.reversal_of_id
                 where r.sale_id=? and r.type='SALE_REVERSAL' and r.direction='OUTFLOW'
                   and o.type='SALE' and o.direction='INFLOW' and o.sale_id=r.sale_id and o.amount=r.amount and r.amount=2200
                """, Integer.class, saleId)).isOne();
        assertThat(jdbcTemplate.queryForObject("select count(*) from audit_log where entity_id=? and action='SALE_CANCELLED'", Integer.class, saleId)).isOne();
    }

    @Test
    void missingLedgerFailsWithoutCancellingSaleOrChangingDevice() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(1500);
        JsonNode device = availableDevice(token, at, "1800.00");
        JsonNode sale = registerSale(token, device, "2200.00", at);
        UUID saleId = UUID.fromString(sale.path("id").asText());
        doReturn(Optional.empty()).when(transactionRepository).findActiveSaleTransaction(saleId);
        JsonNode sold = device(token, device.path("id").asText());
        mockMvc.perform(post("/api/v1/devices/{id}/sale/cancel", device.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("deviceVersion", sold.path("version").asLong(), "saleVersion", sale.path("version").asLong(), "reason", "Teste"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALE_LEDGER_MISSING"));
        assertThat(device(token, device.path("id").asText()).path("status").asText()).isEqualTo("VENDIDO");
        assertThat(jdbcTemplate.queryForObject("select status from sale where id=?", String.class, saleId)).isEqualTo("ACTIVE");
        assertThat(saleTransactionCount(saleId, "SALE_REVERSAL")).isZero();
    }

    @Test
    void postgresRejectsInvalidStatesDatesAndDuplicateActiveSale() throws Exception {
        String token = login();
        Instant cutoff = ensureInitialization(token);
        JsonNode pending = createDevice(token, "/api/v1/devices", cutoff.plusSeconds(1), "1000.00");
        directSaleFailure(pending, cutoff.plusSeconds(2), "only an available device can be sold");
        JsonNode available = markAvailable(token, pending);
        directSaleFailure(available, cutoff, "sale date cannot precede purchase date");
        JsonNode imported = markAvailable(token, createDevice(token, "/api/v1/devices/initial-import", cutoff.minusSeconds(10), "1000.00"));
        directSaleFailure(imported, cutoff, "sale must occur after the business cutoff");
        registerMaintenance(token, available, createPart(token), cutoff.plusSeconds(3), "10.00");
        directSaleFailure(available, cutoff.plusSeconds(2), "sale date cannot precede active maintenance");
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            sqlSale(imported, cutoff.plusSeconds(1));
            tx.setRollbackOnly();
        });
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            sqlSale(imported, cutoff.plusSeconds(1));
            sqlSale(imported, cutoff.plusSeconds(2));
        })).isInstanceOf(DataIntegrityViolationException.class).hasStackTraceContaining("duplicate key");
    }

    @Test
    void postgresKeepsCancelledSaleImmutable() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(1600);
        JsonNode device = availableDevice(token, at, "1800.00");
        JsonNode sale = registerSale(token, device, "2200.00", at);
        cancelSale(token, device(token, device.path("id").asText()), sale, "  Correção  ");
        assertThatThrownBy(() -> jdbcTemplate.update("update sale set cancellation_reason='Outro' where id=?", UUID.fromString(sale.path("id").asText())))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("cancelled sale is immutable");
    }

    @Test
    void concurrentSalesProduceExactlyOneSaleLedgerAndAudit() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(1700);
        JsonNode device = availableDevice(token, at, "1800.00");
        String input = json(Map.of("deviceVersion", device.path("version").asLong(), "salePrice", 2200, "soldAt", at));
        java.util.concurrent.Callable<Integer> register = () -> mockMvc.perform(post("/api/v1/devices/{id}/sale", device.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content(input))
                .andReturn().getResponse().getStatus();
        assertThat(race(register, register)).containsExactlyInAnyOrder(201, 409);
        UUID deviceId = UUID.fromString(device.path("id").asText());
        assertThat(jdbcTemplate.queryForObject("select count(*) from sale where device_id=?", Integer.class, deviceId)).isOne();
        assertThat(jdbcTemplate.queryForObject("select count(*) from financial_transaction f join sale s on s.id=f.sale_id where s.device_id=?", Integer.class, deviceId)).isOne();
    }

    @Test
    void concurrentMaintenanceAndEarlierSaleCannotBothCommit() throws Exception {
        String token = login();
        Instant at = ensureInitialization(token).plusSeconds(1800);
        JsonNode device = availableDevice(token, at, "1800.00");
        JsonNode part = createPart(token);
        java.util.concurrent.Callable<Integer> sale = () -> mockMvc.perform(post("/api/v1/devices/{id}/sale", device.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("deviceVersion", device.path("version").asLong(), "salePrice", 2200, "soldAt", at))))
                .andReturn().getResponse().getStatus();
        java.util.concurrent.Callable<Integer> maintenance = () -> mockMvc.perform(post("/api/v1/devices/{id}/maintenances", device.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("performedAt", at.plusSeconds(10), "items", List.of(Map.of("partId", part.path("id").asText(), "cost", 100))))))
                .andReturn().getResponse().getStatus();
        assertThat(race(sale, maintenance)).containsExactlyInAnyOrder(201, 422);
    }

    private List<Integer> race(java.util.concurrent.Callable<Integer> first, java.util.concurrent.Callable<Integer> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (var operation : List.of(first, second)) {
                tasks.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timeout");
                    return operation.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(tasks.get(0).get(30, TimeUnit.SECONDS), tasks.get(1).get(30, TimeUnit.SECONDS));
        }
    }

    private JsonNode cancelSale(String token, JsonNode device, JsonNode sale, String reason) throws Exception {
        return body(mockMvc.perform(post("/api/v1/devices/{id}/sale/cancel", device.path("id").asText())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("deviceVersion", device.path("version").asLong(), "saleVersion", sale.path("version").asLong(), "reason", reason))))
                .andExpect(status().isOk()).andReturn());
    }

    private void rejectCutoff(String token, Instant cutoff) throws Exception {
        JsonNode initialization = body(mockMvc.perform(get("/api/v1/business-initialization")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk()).andReturn());
        mockMvc.perform(patch("/api/v1/business-initialization").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("expectedVersion", initialization.path("version").asLong(), "cutoffAt", cutoff))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INITIALIZATION_CUTOFF_LOCKED"));
    }

    private void directSaleFailure(JsonNode device, Instant at, String expected) {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> sqlSale(device, at)))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining(expected);
    }

    private void sqlSale(JsonNode device, Instant at) {
        UUID user = jdbcTemplate.queryForObject("select id from app_user where username=?", UUID.class, TEST_USERNAME);
        jdbcTemplate.update("insert into sale (device_id,sale_price,sold_at,responsible_user_id,created_by,updated_by) values (?,2000,?,?,?,?)",
                UUID.fromString(device.path("id").asText()), Timestamp.from(at), user, user, user);
    }

    private void saleFailure(String token, JsonNode device, Instant soldAt, String code)
            throws Exception {
        mockMvc.perform(post("/api/v1/devices/{id}/sale", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceVersion", device.path("version").asLong(),
                                "salePrice", 2200, "soldAt", soldAt))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(code));
    }

    private JsonNode registerSale(String token, JsonNode device, String price, Instant soldAt)
            throws Exception {
        return body(mockMvc.perform(post("/api/v1/devices/{id}/sale", device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("deviceVersion", device.path("version").asLong(),
                                "salePrice", new BigDecimal(price), "soldAt", soldAt))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode registerMaintenance(
            String token, JsonNode device, JsonNode part, Instant performedAt, String cost
    ) throws Exception {
        return body(mockMvc.perform(post("/api/v1/devices/{id}/maintenances",
                                device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("performedAt", performedAt, "items", List.of(Map.of(
                                "partId", part.path("id").asText(), "cost", cost))))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode availableDevice(String token, Instant purchasedAt, String price) throws Exception {
        return markAvailable(token, createDevice(token, "/api/v1/devices", purchasedAt, price));
    }

    private JsonNode markAvailable(String token, JsonNode device) throws Exception {
        return body(mockMvc.perform(post("/api/v1/devices/{id}/mark-available",
                                device.path("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("expectedVersion", device.path("version").asLong()))))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode device(String token, String id) throws Exception {
        return body(mockMvc.perform(get("/api/v1/devices/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn());
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

    private JsonNode createPart(String token) throws Exception {
        String suffix = suffix();
        return body(mockMvc.perform(post("/api/v1/parts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "SALE_PART_" + suffix,
                                "name", "Peça venda " + suffix))))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode createDevice(String token, String path, Instant purchasedAt, String price)
            throws Exception {
        String suffix = suffix();
        JsonNode model = body(mockMvc.perform(post("/api/v1/models")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "I_MODEL_" + suffix,
                                "name", "Modelo I " + suffix, "displayOrder", 1))))
                .andExpect(status().isCreated()).andReturn());
        JsonNode color = body(mockMvc.perform(post("/api/v1/colors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "I_COLOR_" + suffix,
                                "name", "Cor I " + suffix))))
                .andExpect(status().isCreated()).andReturn());
        MockMultipartFile metadata = new MockMultipartFile("device", "",
                MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(Map.of(
                "modelId", model.path("id").asText(), "colorId", color.path("id").asText(),
                "storageGb", 256, "purchasePrice", new BigDecimal(price), "purchasedAt", purchasedAt,
                "faceIdWorking", true, "originalScreen", true, "originalBattery", true,
                "batteryHealthPercent", 90, "initialStatus", "PENDENTE_MANUTENCAO")));
        return body(mockMvc.perform(multipart(path).file(metadata)
                        .file(photo("front.png")).file(photo("back.png"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated()).andReturn());
    }

    private int activeSaleLedger(UUID saleId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from financial_transaction original
                 where original.sale_id = ? and original.type = 'SALE'
                   and not exists (select 1 from financial_transaction reversal
                                    where reversal.reversal_of_id = original.id)
                """, Integer.class, saleId);
    }

    private int saleTransactionCount(UUID saleId, String type) {
        return jdbcTemplate.queryForObject("""
                select count(*) from financial_transaction where sale_id = ? and type = ?
                """, Integer.class, saleId, type);
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
