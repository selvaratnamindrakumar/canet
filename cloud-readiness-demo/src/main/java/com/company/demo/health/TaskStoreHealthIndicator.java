package com.company.demo.health;

import com.company.demo.service.TaskService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for the in-memory task store.
 *
 * <p><strong>Cloud Note:</strong> In a real AWS deployment this would probe
 * the downstream database (DynamoDB, RDS) or message broker (SQS) and report
 * their reachability. ECS and ALB use the {@code /actuator/health} endpoint
 * for container health checks and target group health checks respectively.</p>
 *
 * <p>The indicator is automatically discovered by Spring Boot Actuator
 * and surfaced at {@code /actuator/health/taskStore}.</p>
 */
@Component
public class TaskStoreHealthIndicator implements HealthIndicator {

    private final TaskService taskService;

    public TaskStoreHealthIndicator(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public Health health() {
        try {
            int count = taskService.count();
            return Health.up()
                    .withDetail("type", "in-memory")
                    .withDetail("taskCount", count)
                    .withDetail("note", "Replace with DynamoDB/RDS probe in production")
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
