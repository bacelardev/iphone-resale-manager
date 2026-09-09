package io.github.bacelardev.iphoneresale.web;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PostgresIntegrationTest {

    static final String TEST_USERNAME = String.join(".", "socio", "teste");
    static final String TEST_PASSWORD = UUID.nameUUIDFromBytes(
            "integration-auth-fixture".getBytes(StandardCharsets.US_ASCII))
            .toString().replace("-", "").repeat(4);

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("iphone_resale_test")
                    .withUsername("iphone_resale_test")
                    .withPassword("integration-test-only");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("app.bootstrap.enabled", () -> "true");
        registry.add("app.bootstrap.name", () -> "Sócio de Teste");
        registry.add("app.bootstrap.username", () -> TEST_USERNAME);
        registry.add("app.bootstrap.password", () -> TEST_PASSWORD);
        registry.add("app.cors.allowed-origins", () -> "https://app.example.test");
        registry.add("app.photo-storage.root", () ->
                System.getProperty("java.io.tmpdir") + "/iphone-resale-test-photos");
        registry.add("app.photo-storage.signing-secret", () ->
                "integration-test-photo-signing-secret-32-chars");
    }
}
