package com.canet.validator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Validator service — receives hash registrations from the generator and
 * answers existence queries from the Diosma middle tier.
 *
 * Runs as a standalone Spring Boot web application on port 8080 (default).
 * The generator connects to this service; it has no pcap dependency.
 */
@SpringBootApplication
public class ValidatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValidatorApplication.class, args);
    }
}
