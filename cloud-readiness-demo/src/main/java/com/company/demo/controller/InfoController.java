package com.company.demo.controller;

import com.company.demo.service.TaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Exposes runtime environment information.
 *
 * <p>Demonstrates how environment variables injected by ECS task definitions
 * or Kubernetes ConfigMaps are consumed by the application at runtime —
 * a key 12-Factor App pattern (Factor III: Config).</p>
 *
 * <p>Endpoint: {@code GET /api/v1/info}</p>
 */
@RestController
@RequestMapping("/api/v1/info")
public class InfoController {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${app.environment:local}")
    private String appEnvironment;

    @Value("${app.region:us-east-1}")
    private String awsRegion;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    private final TaskService taskService;

    public InfoController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Map<String, Object> info() {
        return Map.of(
                "application", "cloud-readiness-demo",
                "version", appVersion,
                "profile", activeProfile,
                "environment", appEnvironment,
                "region", awsRegion,
                "taskCount", taskService.count(),
                "serverTime", Instant.now().toString(),
                "cloudReadinessFeatures", Map.of(
                        "healthProbes", true,
                        "externalConfig", true,
                        "structuredLogging", true,
                        "gracefulShutdown", true,
                        "prometheusMetrics", true,
                        "dockerLayeredJar", true,
                        "statelessDesign", true
                )
        );
    }
}
