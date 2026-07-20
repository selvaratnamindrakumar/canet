package com.canet.validator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Validator service — receives hash registrations from the generator,
 * persists them to MySQL, and notifies Diosma asynchronously.
 *
 * @EnableAsync activates the @Async method in DiosmaClient so Diosma
 * notifications run on a separate thread and do not block the 201 response.
 */
@SpringBootApplication
@EnableAsync
public class ValidatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValidatorApplication.class, args);
    }
}
