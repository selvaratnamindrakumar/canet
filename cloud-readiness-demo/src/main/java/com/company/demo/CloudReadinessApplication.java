package com.company.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Cloud Readiness Demo application.
 *
 * <p>This application demonstrates 12-Factor App principles and
 * Docker/AWS-ready patterns for a Spring Boot migration PoC.</p>
 *
 * <p>Cloud Readiness checklist implemented here:
 * <ul>
 *   <li>✅ Stateless design — no in-process session state</li>
 *   <li>✅ Externalised configuration via environment variables</li>
 *   <li>✅ Health &amp; readiness probes via Spring Actuator</li>
 *   <li>✅ Structured JSON logging</li>
 *   <li>✅ Graceful shutdown support</li>
 *   <li>✅ Prometheus metrics endpoint</li>
 *   <li>✅ Docker layered JAR packaging</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
public class CloudReadinessApplication {

    private static final Logger log = LoggerFactory.getLogger(CloudReadinessApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CloudReadinessApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("🚀 Cloud Readiness Demo is UP. Profile={}, Port={}",
                System.getProperty("spring.profiles.active", "default"),
                System.getProperty("server.port", "8080"));
    }
}
