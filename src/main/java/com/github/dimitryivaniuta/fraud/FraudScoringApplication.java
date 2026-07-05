package com.github.dimitryivaniuta.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the real-time fraud scoring and decision service.
 */
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudScoringApplication {

    /**
     * Bootstraps the Spring application context.
     *
     * @param args command-line arguments supplied by the runtime
     */
    public static void main(final String[] args) {
        SpringApplication.run(FraudScoringApplication.class, args);
    }
}
