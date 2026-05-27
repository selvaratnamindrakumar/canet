package com.company.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;

/**
 * Application configuration.
 *
 * <p>Demonstrates Factor III (Config) — all environment-specific values
 * arrive via environment variables or AWS SSM Parameter Store, never
 * hard-coded in the source tree.</p>
 *
 * <p>In ECS the values below are set in the task definition's
 * {@code environment} or {@code secrets} (for sensitive values via
 * AWS Secrets Manager) blocks.</p>
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Value("${app.environment:local}")
    private String environment;

    @Value("${app.region:us-east-1}")
    private String region;

    @Value("${app.version:1.0.0}")
    private String version;

    @PostConstruct
    public void logStartupConfig() {
        // Never log secrets — only non-sensitive config is logged here
        log.info("AppConfig loaded — environment={}, region={}, version={}",
                environment, region, version);
    }
}
