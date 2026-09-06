package io.github.bacelardev.iphoneresale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IphoneResaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(IphoneResaleApplication.class, args);
    }
}
