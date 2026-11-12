package com.aiguardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiGuardianApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiGuardianApplication.class, args);
    }
}
