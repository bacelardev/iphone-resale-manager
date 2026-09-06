package io.github.bacelardev.iphoneresale.config;

import io.github.bacelardev.iphoneresale.application.service.auth.BootstrapFirstUserService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BootstrapConfiguration {

    @Bean
    ApplicationRunner bootstrapFirstUserRunner(BootstrapFirstUserService bootstrapService) {
        return arguments -> bootstrapService.bootstrapIfRequired();
    }
}
