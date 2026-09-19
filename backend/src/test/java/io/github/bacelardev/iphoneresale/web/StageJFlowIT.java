package io.github.bacelardev.iphoneresale.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StageJFlowIT extends PostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void completesInitializationWithRealCashAndHistoricalCapitalAtomically() throws Exception {
        String token = login();
        JsonNode initialization = initialization(token);
        if ("NOT_STARTED".equals(initialization.path("status").asText())) {
            initialization = body(mockMvc.perform(post("/api/v1/business-initialization/start")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "cutoffAt", Instant.now().minusSeconds(7200)))))
                    .andExpect(status().isCreated()).andReturn());
        }
        UUID ownerId = jdbcTemplate.queryForObject(
                "select id from app_user where username = ?", UUID.class, TEST_USERNAME);

        JsonNode completed = body(mockMvc.perform(post("/api/v1/business-initialization/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedVersion", initialization.path("version").asLong(),
                                "declaredCashBalance", new BigDecimal("4000.00"),
                                "ownerCapitalOpenings", List.of(Map.of(
                                        "ownerUserId", ownerId,
                                        "historicalContributionAmount", new BigDecimal("9000.00"),
                                        "historicalWithdrawalAmount", new BigDecimal("1000.00")))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.declaredCashBalance").value(4000.00))
                .andExpect(jsonPath("$.ownerCapitalOpenings[0].ownerUser.id")
                        .value(ownerId.toString()))
                .andReturn());

        assertThat(completed.path("openingBalanceTransactionId").isTextual()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from financial_transaction where type = 'OPENING_BALANCE'",
                Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from owner_capital_opening", Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from financial_transaction where type in " +
                        "('OWNER_CONTRIBUTION','OWNER_WITHDRAWAL')",
                Integer.class)).isZero();
    }

    @Test
    @Order(2)
    void blocksSecondCompletionAndHistoricalImportAfterCompletion() throws Exception {
        String token = login();
        JsonNode initialization = initialization(token);

        mockMvc.perform(post("/api/v1/business-initialization/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "expectedVersion", initialization.path("version").asLong(),
                                "declaredCashBalance", 0,
                                "ownerCapitalOpenings", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_ALREADY_INITIALIZED"));
    }

    @Test
    @Order(3)
    void recordsOwnerMovementsAdjustmentAndSingleManualReversal() throws Exception {
        String token = login();
        JsonNode initialization = initialization(token);
        Instant cutoff = Instant.parse(initialization.path("cutoffAt").asText());
        UUID ownerId = jdbcTemplate.queryForObject(
                "select id from app_user where username = ?", UUID.class, TEST_USERNAME);

        JsonNode contribution = create(token, "/api/v1/financial/contributions", Map.of(
                "ownerUserId", ownerId,
                "amount", new BigDecimal("500.00"),
                "occurredAt", cutoff.plusSeconds(60),
                "description", "Aporte para capital de giro."));
        assertThat(contribution.path("ownerUser").path("id").asText())
                .isEqualTo(ownerId.toString());
        assertThat(contribution.path("createdBy").path("id").asText())
                .isEqualTo(ownerId.toString());

        create(token, "/api/v1/financial/withdrawals", Map.of(
                "ownerUserId", ownerId,
                "amount", new BigDecimal("5000.00"),
                "occurredAt", cutoff.plusSeconds(120),
                "description", "Retirada permitida mesmo com caixa negativo."));
        create(token, "/api/v1/financial/adjustments", Map.of(
                "direction", "INFLOW",
                "amount", new BigDecimal("250.00"),
                "occurredAt", cutoff.plusSeconds(180),
                "description", "Ajuste de conferência."));

        JsonNode reversal = create(token, "/api/v1/financial/adjustments", Map.of(
                "reversalOfTransactionId", contribution.path("id").asText(),
                "occurredAt", cutoff.plusSeconds(240),
                "description", "Estorno do aporte registrado em duplicidade."));
        assertThat(reversal.path("type").asText()).isEqualTo("MANUAL_ADJUSTMENT");
        assertThat(reversal.path("direction").asText()).isEqualTo("OUTFLOW");
        assertThat(reversal.path("amount").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(reversal.path("reversalOfId").asText())
                .isEqualTo(contribution.path("id").asText());

        mockMvc.perform(post("/api/v1/financial/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "reversalOfTransactionId", contribution.path("id").asText(),
                                "occurredAt", cutoff.plusSeconds(300),
                                "description", "Segundo estorno indevido."))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("FINANCIAL_TRANSACTION_ALREADY_REVERSED"));
    }

    @Test
    @Order(4)
    void listsFiltersAndSummarizesWithBahiaPeriodAndNullableMargin() throws Exception {
        String token = login();
        JsonNode initialization = initialization(token);
        Instant cutoff = Instant.parse(initialization.path("cutoffAt").asText());
        Instant to = cutoff.plusSeconds(3600);

        mockMvc.perform(get("/api/v1/financial/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", cutoff.toString())
                        .param("to", to.toString())
                        .param("direction", "OUTFLOW")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sort", "amount,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/v1/financial/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", cutoff.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.from").value(cutoff.toString()))
                .andExpect(jsonPath("$.period.to").value(to.toString()))
                .andExpect(jsonPath("$.period.businessTimezone").value("America/Bahia"))
                .andExpect(jsonPath("$.from").doesNotExist())
                .andExpect(jsonPath("$.to").doesNotExist())
                .andExpect(jsonPath("$.openingBalance").value(0.00))
                .andExpect(jsonPath("$.closingBalance").value(-750.00))
                .andExpect(jsonPath("$.revenue").value(0.00))
                .andExpect(jsonPath("$.profit").value(0.00))
                .andExpect(jsonPath("$.marginPercent").value(
                        org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.stockCapital").value(0.00));

        mockMvc.perform(get("/api/v1/financial/summary")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("from", cutoff.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/financial/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("sort", "description,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    @Order(5)
    void validatesMoneyModesOwnerAndOperationalBoundary() throws Exception {
        String token = login();
        JsonNode initialization = initialization(token);
        Instant cutoff = Instant.parse(initialization.path("cutoffAt").asText());
        UUID ownerId = jdbcTemplate.queryForObject(
                "select id from app_user where username = ?", UUID.class, TEST_USERNAME);

        for (String amount : List.of("0", "-1", "1.001", "1000000000000")) {
            mockMvc.perform(post("/api/v1/financial/contributions")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of(
                                    "ownerUserId", ownerId,
                                    "amount", new BigDecimal(amount),
                                    "occurredAt", cutoff.plusSeconds(600),
                                    "description", "Valor inválido."))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        mockMvc.perform(post("/api/v1/financial/contributions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "ownerUserId", ownerId,
                                "amount", 10,
                                "occurredAt", cutoff,
                                "description", "Fora do período."))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code")
                        .value("FINANCIAL_OPERATION_REQUIRES_OPERATIONAL_PERIOD"));

        mockMvc.perform(post("/api/v1/financial/adjustments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "direction", "INFLOW",
                                "amount", 10,
                                "reversalOfTransactionId", UUID.randomUUID(),
                                "occurredAt", cutoff.plusSeconds(700),
                                "description", "Modos conflitantes."))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FINANCIAL_OPERATION"));

        mockMvc.perform(get("/api/v1/financial/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/financial/contributions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode create(String token, String path, Object input) throws Exception {
        return body(mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(input)))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode initialization(String token) throws Exception {
        return body(mockMvc.perform(get("/api/v1/business-initialization")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn());
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

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
