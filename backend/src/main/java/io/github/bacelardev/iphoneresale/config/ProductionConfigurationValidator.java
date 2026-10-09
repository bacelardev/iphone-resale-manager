package io.github.bacelardev.iphoneresale.config;

import io.github.bacelardev.iphoneresale.config.properties.BootstrapProperties;
import io.github.bacelardev.iphoneresale.config.properties.CorsProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@Profile("prod")
public class ProductionConfigurationValidator implements ApplicationRunner {

    private final CorsProperties cors;
    private final BootstrapProperties bootstrap;
    private final Environment environment;

    public ProductionConfigurationValidator(
            CorsProperties cors,
            BootstrapProperties bootstrap,
            Environment environment
    ) {
        this.cors = cors;
        this.bootstrap = bootstrap;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cors.allowedOrigins().size() != 1) {
            throw new IllegalStateException("Production requires exactly one frontend CORS origin");
        }
        URI origin = URI.create(cors.allowedOrigins().getFirst());
        if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null
                || origin.getUserInfo() != null || origin.getPort() != -1
                || !(origin.getPath() == null || origin.getPath().isEmpty())
                || origin.getQuery() != null || origin.getFragment() != null) {
            throw new IllegalStateException("Production CORS origin must be one HTTPS origin without path");
        }
        if (bootstrap.isEnabled()) {
            throw new IllegalStateException("Bootstrap must be disabled in production");
        }
        if (!"imagekit".equals(environment.getProperty("app.photo-storage.provider"))) {
            throw new IllegalStateException("Production photo storage must use ImageKit");
        }
        String databaseUrl = environment.getRequiredProperty("spring.datasource.url");
        if (!databaseUrl.contains("sslmode=require")) {
            throw new IllegalStateException("Production PostgreSQL URL must require SSL");
        }
    }
}
